package ai.applypilot.jobs;

import ai.applypilot.profile.ProfileRepository;
import ai.applypilot.resume.ResumeRepository;
import ai.applypilot.resume.ResumeVersionRepository;
import ai.applypilot.resume.TailoringEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class JobMatchService {

    private final ProfileRepository profileRepo;
    private final ResumeRepository resumeRepo;
    private final ResumeVersionRepository versionRepo;
    private final JdbcTemplate jdbc;

    public JobMatchService(ProfileRepository profileRepo,
                           ResumeRepository resumeRepo,
                           ResumeVersionRepository versionRepo,
                           JdbcTemplate jdbc) {
        this.profileRepo = profileRepo;
        this.resumeRepo = resumeRepo;
        this.versionRepo = versionRepo;
        this.jdbc = jdbc;
    }

    public record MatchResult(
        BigDecimal score,
        List<String> matchingRequirements,
        List<String> missingRequirements,
        List<String> partialMatches,
        String eligibilityStatus,
        List<String> eligibilityReasons,
        List<String> relevantProjects,
        String roleAlignment,
        String locationAlignment,
        String disclaimer
    ) {}

    public MatchResult calculateMatch(UUID userId, String jobTitle, String jobLocation, String jobDescription) {
        var profile = profileRepo.findById(userId).orElse(null);
        String desc = jobDescription != null ? jobDescription : "";
        String title = jobTitle != null ? jobTitle : "";
        String loc = jobLocation != null ? jobLocation : "";

        // Separate required vs preferred skills from description
        var extractedSkills = JobIngestionService.extractSkills(desc);
        List<String> reqSkills = extractedSkills.getOrDefault("required", List.of());
        List<String> prefSkills = extractedSkills.getOrDefault("preferred", List.of());

        // Also extract generic keywords from description
        var keywords = TailoringEngine.extractKeywords(desc);

        // Candidate-provided knowledge used to calculate explainable alignment.
        Set<String> candidateTokens = new HashSet<>();

        // 1. Profile information
        if (profile != null) {
            if (profile.dto().preferredRoles() != null) {
                profile.dto().preferredRoles().forEach(r -> candidateTokens.addAll(tokenize(r)));
            }
            if (profile.dto().location() != null) {
                candidateTokens.addAll(tokenize(profile.dto().location()));
            }
        }

        // 2. Verified skills from skill_records
        try {
            List<Map<String, Object>> skills = jdbc.queryForList("SELECT name FROM skill_records WHERE user_id = ? AND verified = TRUE", userId);
            for (var row : skills) {
                String name = (String) row.get("name");
                if (name != null) {
                    candidateTokens.addAll(tokenize(name));
                }
            }
        } catch (Exception ignored) {}

        // 3. Work experiences
        try {
            List<Map<String, Object>> exps = jdbc.queryForList("SELECT company, title, description FROM work_experiences WHERE user_id = ?", userId);
            for (var row : exps) {
                candidateTokens.addAll(tokenize((String) row.get("company")));
                candidateTokens.addAll(tokenize((String) row.get("title")));
                candidateTokens.addAll(tokenize((String) row.get("description")));
            }
        } catch (Exception ignored) {}

        // 4. Candidate projects
        List<String> matchedProjects = new ArrayList<>();
        try {
            List<Map<String, Object>> projs = jdbc.queryForList("SELECT name, description, technologies FROM projects WHERE user_id = ?", userId);
            for (var row : projs) {
                String pName = (String) row.get("name");
                String pDesc = (String) row.get("description");
                Object techsObj = row.get("technologies");
                String pTechs = techsObj != null ? techsObj.toString() : "";

                candidateTokens.addAll(tokenize(pName));
                candidateTokens.addAll(tokenize(pDesc));
                candidateTokens.addAll(tokenize(pTechs));

                // Check if this project matches key skills in the job
                boolean relevant = false;
                for (String req : reqSkills) {
                    if ((pDesc != null && pDesc.toLowerCase(Locale.ROOT).contains(req.toLowerCase(Locale.ROOT))) ||
                        (pTechs.toLowerCase(Locale.ROOT).contains(req.toLowerCase(Locale.ROOT)))) {
                        relevant = true;
                        break;
                    }
                }
                if (relevant && pName != null) {
                    matchedProjects.add(pName + (pTechs.isBlank() ? "" : " (" + pTechs + ")"));
                }
            }
        } catch (Exception ignored) {}

        // 5. Resume extracted text
        try {
            var resumes = resumeRepo.findAllByUserIdOrderByCreatedAtDesc(userId);
            if (!resumes.isEmpty()) {
                var latestVer = versionRepo.findFirstByResumeIdOrderByVersionNumberDesc(resumes.get(0).getId());
                if (latestVer.isPresent() && latestVer.get().getExtractedText() != null) {
                    candidateTokens.addAll(tokenize(latestVer.get().getExtractedText()));
                }
            }
        } catch (Exception ignored) {}

        // --- SCORING & EXPLANATIONS ---

        // A. Role Alignment (max 20 pts)
        double roleScore = 10.0;
        String roleAlign = "Unspecified alignment";
        if (!title.isBlank()) {
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            if (profile != null && profile.dto().preferredRoles() != null && !profile.dto().preferredRoles().isEmpty()) {
                boolean direct = profile.dto().preferredRoles().stream()
                    .anyMatch(r -> lowerTitle.contains(r.toLowerCase(Locale.ROOT)) || r.toLowerCase(Locale.ROOT).contains(lowerTitle));
                if (direct) {
                    roleScore = 20.0;
                    roleAlign = "Direct match with candidate's preferred roles";
                } else {
                    roleScore = 14.0;
                    roleAlign = "Adjacent or related engineering role";
                }
            }
        }

        // B. Location Alignment (max 15 pts)
        double locScore = 8.0;
        String locAlign = "Location requires review";
        String lowerLoc = loc.toLowerCase(Locale.ROOT);
        if (lowerLoc.contains("remote") || desc.toLowerCase(Locale.ROOT).contains("fully remote")) {
            locScore = 15.0;
            locAlign = "Remote eligible position";
        } else if (profile != null && profile.dto().preferredLocations() != null) {
            boolean matchedLoc = profile.dto().preferredLocations().stream()
                .anyMatch(l -> lowerLoc.contains(l.toLowerCase(Locale.ROOT)));
            if (matchedLoc) {
                locScore = 15.0;
                locAlign = "Matches preferred candidate location: " + loc;
            }
        }

        // C. Required Skills (max 30 pts)
        List<String> matchingReqs = new ArrayList<>();
        List<String> missingReqs = new ArrayList<>();

        if (!reqSkills.isEmpty()) {
            for (String req : reqSkills) {
                if (containsRequirement(candidateTokens, req)) {
                    matchingReqs.add(req);
                } else {
                    missingReqs.add(req);
                }
            }
        } else {
            // Fallback to top keywords if no structured skills found
            int count = 0;
            for (String kw : keywords) {
                if (count++ > 15) break;
                if (candidateTokens.contains(kw)) {
                    matchingReqs.add(kw);
                } else {
                    missingReqs.add(kw);
                }
            }
        }

        double reqRatio = (matchingReqs.size() + missingReqs.size()) > 0
            ? (double) matchingReqs.size() / (double) (matchingReqs.size() + missingReqs.size())
            : 0.5;
        double reqScore = reqRatio * 30.0;

        // D. Preferred / Bonus Skills (max 15 pts)
        List<String> partialMatches = new ArrayList<>();
        for (String pref : prefSkills) {
            if (containsRequirement(candidateTokens, pref)) {
                partialMatches.add(pref);
            }
        }
        double prefRatio = !prefSkills.isEmpty() ? (double) partialMatches.size() / (double) prefSkills.size() : 0.4;
        double prefScore = Math.min(15.0, prefRatio * 15.0);

        // E. Education, Graduation Year & Internship Eligibility (max 10 pts)
        double eduScore = 5.0; // Neutral contribution: missing education data must not imply eligibility.
        Integer gradYear = profile != null ? profile.dto().graduationYear() : null;
        EligibilityAnalyzer.Result eligibility = new EligibilityAnalyzer().analyze(title, desc, gradYear);

        // F. Relevant Experience & Projects (max 10 pts)
        double projectScore = !matchedProjects.isEmpty() ? Math.min(10.0, matchedProjects.size() * 5.0) : 4.0;

        // Total sum
        double total = Math.min(100.0, roleScore + locScore + reqScore + prefScore + eduScore + projectScore);

        String disclaimer = "Estimated criteria alignment based on candidate-provided records. This is an objective alignment aid, not a guaranteed ATS score or prediction of employer selection.";

        return new MatchResult(
            BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP),
            matchingReqs.stream().limit(15).toList(),
            missingReqs.stream().limit(10).toList(),
            partialMatches.stream().limit(10).toList(),
            eligibility.status(),
            eligibility.reasons(),
            matchedProjects.stream().limit(5).toList(),
            roleAlign,
            locAlign,
            disclaimer
        );
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] parts = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#./ ]", " ").split("\\s+");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            if (p.length() >= 2) set.add(p);
        }
        return set;
    }

    private boolean containsRequirement(Set<String> candidateTokens, String requirement) {
        Set<String> requiredTokens = tokenize(requirement);
        return !requiredTokens.isEmpty() && candidateTokens.containsAll(requiredTokens);
    }
}
