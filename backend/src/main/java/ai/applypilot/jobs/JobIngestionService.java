package ai.applypilot.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JobIngestionService.class);
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final SavedJobRepository jobRepo;
    private final JobIngestionLogRepository logRepo;
    private final JobMatchService matchService;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    private static final Set<String> COMMON_SKILLS = Set.of(
        "Java", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue", "Spring Boot",
        "Node.js", "C++", "C#", ".NET", "Go", "Rust", "SQL", "PostgreSQL", "MySQL", "MongoDB",
        "Redis", "Kafka", "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Linux", "Git",
        "GraphQL", "REST APIs", "Microservices", "CI/CD", "Terraform", "Selenium", "Cypress"
    );

    public JobIngestionService(SavedJobRepository jobRepo,
                               JobIngestionLogRepository logRepo,
                               JobMatchService matchService,
                               ObjectMapper mapper) {
        this.jobRepo = jobRepo;
        this.logRepo = logRepo;
        this.matchService = matchService;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    public record IngestResult(
        String source,
        String sourceUrl,
        int importedCount,
        int skippedCount,
        List<SavedJob> importedJobs,
        String status,
        String message
    ) {}

    public CompanyResearchParser.Research researchCompany(String companyPageUrl) {
        validateSafePublicUrl(companyPageUrl);
        try {
            String html = fetchHttp(companyPageUrl);
            return new CompanyResearchParser(mapper).parse(html, companyPageUrl, Instant.now());
        } catch (Exception ex) {
            log.warn("Company metadata could not be retrieved from the supplied public page ({})", ex.getClass().getSimpleName());
            return new CompanyResearchParser.Research("", "UNAVAILABLE", companyPageUrl, Instant.now(), List.of());
        }
    }

    // Public Greenhouse Board API or single job ingestion
    @Transactional
    public IngestResult ingestGreenhouse(UUID userId, String boardTokenOrUrl) {
        String token = extractGreenhouseToken(boardTokenOrUrl);
        String singleJobId = extractGreenhouseJobId(boardTokenOrUrl);

        if (token == null || token.isBlank()) {
            recordLog(userId, "Greenhouse", boardTokenOrUrl, "FAILED", 0, 0, "Could not extract Greenhouse board token.");
            return new IngestResult("Greenhouse", boardTokenOrUrl, 0, 0, List.of(), "FAILED", "Invalid Greenhouse board token or URL.");
        }

        try {
            if (singleJobId != null && !singleJobId.isBlank()) {
                // Fetch single job via public API
                String apiUrl = "https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs/" + singleJobId;
                String body = fetchHttp(apiUrl);
                JsonNode root = mapper.readTree(body);

                SavedJob job = parseGreenhouseJobNode(userId, token, root);
                if (job == null) {
                    recordLog(userId, "Greenhouse", apiUrl, "PARTIAL", 0, 1, "Duplicate or unparsable job");
                    return new IngestResult("Greenhouse", boardTokenOrUrl, 0, 1, List.of(), "PARTIAL", "Job already saved or skipped.");
                }
                setImportMetadata(job, boardTokenOrUrl, job.getJobUrl());
                jobRepo.save(job);
                recordLog(userId, "Greenhouse", apiUrl, "SUCCESS", 1, 0, null);
                return new IngestResult("Greenhouse", boardTokenOrUrl, 1, 0, List.of(job), "SUCCESS", "Successfully imported job from Greenhouse.");
            } else {
                // Fetch board job list
                String apiUrl = "https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs?content=true";
                String body = fetchHttp(apiUrl);
                JsonNode root = mapper.readTree(body);
                JsonNode jobsArray = root.path("jobs");

                List<SavedJob> imported = new ArrayList<>();
                int skipped = 0;

                if (jobsArray.isArray()) {
                    for (JsonNode node : jobsArray) {
                        SavedJob job = parseGreenhouseJobNode(userId, token, node);
                        if (job != null) {
                            imported.add(job);
                        } else {
                            skipped++;
                        }
                    }
                }

                String status = imported.isEmpty() && skipped > 0 ? "PARTIAL" : (imported.isEmpty() ? "FAILED" : "SUCCESS");
                recordLog(userId, "Greenhouse", apiUrl, status, imported.size(), skipped, null);
                return new IngestResult("Greenhouse", boardTokenOrUrl, imported.size(), skipped, imported, status,
                    "Imported " + imported.size() + " jobs (" + skipped + " duplicates/skipped).");
            }
        } catch (Exception e) {
            log.warn("Greenhouse ingestion failed ({})", e.getClass().getSimpleName());
            recordLog(userId, "Greenhouse", boardTokenOrUrl, "FAILED", 0, 0, "Public board request failed.");
            return new IngestResult("Greenhouse", boardTokenOrUrl, 0, 0, List.of(), "FAILED", "The public Greenhouse board could not be retrieved. Check the board URL and try manual entry.");
        }
    }

    // Public Lever Postings API ingestion
    @Transactional
    public IngestResult ingestLever(UUID userId, String siteOrUrl) {
        String site = extractLeverSite(siteOrUrl);
        String postingId = extractLeverPostingId(siteOrUrl);

        if (site == null || site.isBlank()) {
            recordLog(userId, "Lever", siteOrUrl, "FAILED", 0, 0, "Could not extract Lever site name.");
            return new IngestResult("Lever", siteOrUrl, 0, 0, List.of(), "FAILED", "Invalid Lever site name or URL.");
        }

        try {
            if (postingId != null && !postingId.isBlank()) {
                String apiUrl = "https://api.lever.co/v0/postings/" + site + "/" + postingId;
                String body = fetchHttp(apiUrl);
                JsonNode root = mapper.readTree(body);

                SavedJob job = parseLeverJobNode(userId, site, root);
                if (job == null) {
                    recordLog(userId, "Lever", apiUrl, "PARTIAL", 0, 1, "Duplicate or unparsable posting.");
                    return new IngestResult("Lever", siteOrUrl, 0, 1, List.of(), "PARTIAL", "Job already saved or skipped.");
                }
                setImportMetadata(job, siteOrUrl, job.getJobUrl());
                jobRepo.save(job);
                recordLog(userId, "Lever", apiUrl, "SUCCESS", 1, 0, null);
                return new IngestResult("Lever", siteOrUrl, 1, 0, List.of(job), "SUCCESS", "Successfully imported job from Lever.");
            } else {
                String apiUrl = "https://api.lever.co/v0/postings/" + site + "?mode=json";
                String body = fetchHttp(apiUrl);
                JsonNode root = mapper.readTree(body);

                List<SavedJob> imported = new ArrayList<>();
                int skipped = 0;

                if (root.isArray()) {
                    for (JsonNode node : root) {
                        SavedJob job = parseLeverJobNode(userId, site, node);
                        if (job != null) {
                            imported.add(job);
                        } else {
                            skipped++;
                        }
                    }
                }

                String status = imported.isEmpty() && skipped > 0 ? "PARTIAL" : (imported.isEmpty() ? "FAILED" : "SUCCESS");
                recordLog(userId, "Lever", apiUrl, status, imported.size(), skipped, null);
                return new IngestResult("Lever", siteOrUrl, imported.size(), skipped, imported, status,
                    "Imported " + imported.size() + " jobs from Lever (" + skipped + " duplicates/skipped).");
            }
        } catch (Exception e) {
            log.warn("Lever ingestion failed ({})", e.getClass().getSimpleName());
            recordLog(userId, "Lever", siteOrUrl, "FAILED", 0, 0, "Public postings request failed.");
            return new IngestResult("Lever", siteOrUrl, 0, 0, List.of(), "FAILED", "The public Lever postings could not be retrieved. Check the site URL and try manual entry.");
        }
    }

    // Public RSS / XML Job Feed Ingestion
    @Transactional
    public IngestResult ingestRssFeed(UUID userId, String feedUrl) {
        validateSafePublicUrl(feedUrl);

        try {
            String xmlContent = fetchHttp(feedUrl);
            List<RssJobFeedParser.FeedItem> items = new RssJobFeedParser().parse(xmlContent);
            List<SavedJob> imported = new ArrayList<>();
            int skipped = 0;

            for (RssJobFeedParser.FeedItem item : items) {
                String title = item.title();
                String link = item.link();
                String guid = item.id();
                if (title.isBlank()) {
                    skipped++;
                    continue;
                }

                // Clean HTML tags from description
                String cleanDesc = stripHtml(item.description());

                // Duplicate check
                if (isDuplicate(userId, "RSS Feed", guid, link, title, "")) {
                    skipped++;
                    continue;
                }

                SavedJob job = new SavedJob(userId, title, extractCompanyFromTitle(title), "Remote / Unspecified", link, cleanDesc, "RSS Feed");
                setImportMetadata(job, feedUrl, link);
                job.setExternalJobId(guid);
                job.setPostedAt(item.publishedAt());

                var skills = extractSkills(cleanDesc);
                job.setRequiredSkills(skills.get("required"));
                job.setPreferredSkills(skills.get("preferred"));

                var match = matchService.calculateMatch(userId, title, job.getLocation(), cleanDesc);
                job.setMatchScore(match.score());

                jobRepo.save(job);
                imported.add(job);
            }

            String status = imported.isEmpty() && skipped > 0 ? "PARTIAL" : (imported.isEmpty() ? "FAILED" : "SUCCESS");
            recordLog(userId, "RSS Feed", feedUrl, status, imported.size(), skipped, null);
            return new IngestResult("RSS Feed", feedUrl, imported.size(), skipped, imported, status,
                "Parsed feed: imported " + imported.size() + " opportunities (" + skipped + " skipped).");

        } catch (Exception e) {
            log.warn("RSS ingestion failed ({})", e.getClass().getSimpleName());
            recordLog(userId, "RSS Feed", feedUrl, "FAILED", 0, 0, "Public feed could not be parsed or retrieved.");
            return new IngestResult("RSS Feed", feedUrl, 0, 0, List.of(), "FAILED", "The public feed could not be parsed or retrieved. Check the feed URL and try manual entry.");
        }
    }

    // Structured Job URL Import (detects Greenhouse, Lever, JSON-LD JobPosting, or OpenGraph)
    @Transactional
    public IngestResult importStructuredJobUrl(UUID userId, String jobUrl) {
        validateSafePublicUrl(jobUrl);

        // 1. Check if it is Greenhouse
        if (jobUrl.contains("greenhouse.io")) {
            return ingestGreenhouse(userId, jobUrl);
        }

        // 2. Check if it is Lever
        if (jobUrl.contains("lever.co")) {
            return ingestLever(userId, jobUrl);
        }

        // 3. Fallback to scraping public structured data (JSON-LD JobPosting or OpenGraph)
        try {
            String html = fetchHttp(jobUrl);

            // Attempt JSON-LD schema extraction
            SavedJob jobFromJsonLd = parseJsonLdJob(userId, jobUrl, html);
            if (jobFromJsonLd != null) {
                if (isDuplicate(userId, jobFromJsonLd.getSource(), jobFromJsonLd.getExternalJobId(), jobUrl, jobFromJsonLd.getTitle(), jobFromJsonLd.getCompany())) {
                    recordLog(userId, "Structured Web Import", jobUrl, "PARTIAL", 0, 1, "Duplicate job");
                    return new IngestResult("Structured Web Import", jobUrl, 0, 1, List.of(), "PARTIAL", "This job is already in your pipeline.");
                }
                var match = matchService.calculateMatch(userId, jobFromJsonLd.getTitle(), jobFromJsonLd.getLocation(), jobFromJsonLd.getDescription());
                jobFromJsonLd.setMatchScore(match.score());
                jobRepo.save(jobFromJsonLd);

                recordLog(userId, "Structured Web Import", jobUrl, "SUCCESS", 1, 0, null);
                return new IngestResult("Structured Web Import", jobUrl, 1, 0, List.of(jobFromJsonLd), "SUCCESS", "Imported job posting via structured metadata.");
            }

            // Fallback to OpenGraph / Meta tags
            SavedJob jobFromMeta = parseMetaTagsJob(userId, jobUrl, html);
            if (jobFromMeta != null) {
                if (isDuplicate(userId, jobFromMeta.getSource(), jobFromMeta.getExternalJobId(), jobUrl, jobFromMeta.getTitle(), jobFromMeta.getCompany())) {
                    recordLog(userId, "Web Meta Import", jobUrl, "PARTIAL", 0, 1, "Duplicate job");
                    return new IngestResult("Web Meta Import", jobUrl, 0, 1, List.of(), "PARTIAL", "This job is already in your pipeline.");
                }
                var match = matchService.calculateMatch(userId, jobFromMeta.getTitle(), jobFromMeta.getLocation(), jobFromMeta.getDescription());
                jobFromMeta.setMatchScore(match.score());
                jobRepo.save(jobFromMeta);

                recordLog(userId, "Web Meta Import", jobUrl, "SUCCESS", 1, 0, null);
                return new IngestResult("Web Meta Import", jobUrl, 1, 0, List.of(jobFromMeta), "SUCCESS", "Imported job details from page metadata.");
            }

            recordLog(userId, "URL Import", jobUrl, "FAILED", 0, 0, "No structured job metadata found on page.");
            return new IngestResult("URL Import", jobUrl, 0, 0, List.of(), "FAILED", "Could not detect structured job posting on this page. Please enter details manually.");

        } catch (Exception e) {
            log.warn("Structured job import failed ({})", e.getClass().getSimpleName());
            recordLog(userId, "URL Import", jobUrl, "FAILED", 0, 0, "Public page could not be parsed or retrieved.");
            return new IngestResult("URL Import", jobUrl, 0, 0, List.of(), "FAILED", "The public page could not be parsed or retrieved. Try another permitted URL or enter the job manually.");
        }
    }

    // Helper: Greenhouse parser
    private SavedJob parseGreenhouseJobNode(UUID userId, String boardToken, JsonNode node) {
        String extId = node.path("id").asText();
        String title = node.path("title").asText();
        if (title.isBlank()) return null;

        String jobUrl = node.path("absolute_url").asText();
        String location = node.path("location").path("name").asText("Unspecified");
        String contentHtml = node.path("content").asText();
        String cleanDesc = stripHtml(contentHtml);

        String company = capitalize(boardToken);

        if (isDuplicate(userId, "Greenhouse", extId, jobUrl, title, company)) {
            return null;
        }

        SavedJob job = new SavedJob(userId, title, company, location, jobUrl, cleanDesc, "Greenhouse");
        job.setExternalJobId(extId);

        var skills = extractSkills(cleanDesc);
        job.setRequiredSkills(skills.get("required"));
        job.setPreferredSkills(skills.get("preferred"));

        String updatedAtStr = node.path("updated_at").asText();
        if (!updatedAtStr.isBlank()) {
            try { job.setPostedAt(Instant.parse(updatedAtStr)); } catch (Exception ignored) {}
        }

        var match = matchService.calculateMatch(userId, title, location, cleanDesc);
        job.setMatchScore(match.score());

        return jobRepo.save(job);
    }

    // Helper: Lever parser
    private SavedJob parseLeverJobNode(UUID userId, String site, JsonNode node) {
        String extId = node.path("id").asText();
        String title = node.path("text").asText();
        if (title.isBlank()) return null;

        String jobUrl = node.path("hostedUrl").asText();
        String location = node.path("categories").path("location").asText("Unspecified");
        String commitment = node.path("categories").path("commitment").asText("Full-time");
        String desc = node.path("descriptionPlain").asText();
        if (desc.isBlank()) {
            desc = stripHtml(node.path("description").asText());
        }

        String company = capitalize(site);

        if (isDuplicate(userId, "Lever", extId, jobUrl, title, company)) {
            return null;
        }

        SavedJob job = new SavedJob(userId, title, company, location, jobUrl, desc, "Lever");
        job.setExternalJobId(extId);
        job.setJobType(commitment);

        var skills = extractSkills(desc);
        job.setRequiredSkills(skills.get("required"));
        job.setPreferredSkills(skills.get("preferred"));

        long createdAtMs = node.path("createdAt").asLong(0);
        if (createdAtMs > 0) {
            job.setPostedAt(Instant.ofEpochMilli(createdAtMs));
        }

        var match = matchService.calculateMatch(userId, title, location, desc);
        job.setMatchScore(match.score());

        return jobRepo.save(job);
    }

    // Helper: JSON-LD Parser
    SavedJob parseJsonLdJob(UUID userId, String pageUrl, String html) {
        Pattern pattern = Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                JsonNode root = mapper.readTree(json);
                JsonNode targetNode = findJobPostingNode(root);

                if (targetNode != null) {
                    String title = targetNode.path("title").asText();
                    String company = targetNode.path("hiringOrganization").path("name").asText("Direct Employer");
                    String desc = stripHtml(targetNode.path("description").asText());
                    String education = structuredText(targetNode.path("educationRequirements"));
                    String experience = structuredText(targetNode.path("experienceRequirements"));
                    String qualifications = structuredText(targetNode.path("qualifications"));
                    String requirements = String.join(" ", java.util.stream.Stream.of(education, experience, qualifications)
                        .filter(value -> value != null && !value.isBlank()).toList());
                    if (!requirements.isBlank() && !desc.toLowerCase(Locale.ROOT).contains(requirements.toLowerCase(Locale.ROOT))) {
                        desc = (desc + "\nRequirements: " + stripHtml(requirements)).trim();
                    }
                    JsonNode address = targetNode.path("jobLocation").path("address");
                    String loc = java.util.stream.Stream.of(
                            address.path("addressLocality").asText(""), address.path("addressRegion").asText(""),
                            address.path("addressCountry").isObject() ? address.path("addressCountry").path("name").asText("") : address.path("addressCountry").asText(""))
                        .filter(value -> value != null && !value.isBlank()).distinct().collect(java.util.stream.Collectors.joining(", "));
                    if (loc.isBlank()) loc = "Remote / Flexible";
                    String employmentType = structuredText(targetNode.path("employmentType"));
                    if (employmentType.isBlank()) employmentType = "Full-time";
                    String datePosted = targetNode.path("datePosted").asText();
                    String applicationUrl = targetNode.path("applicationUrl").asText("");
                    if (applicationUrl.isBlank()) applicationUrl = targetNode.path("url").asText(pageUrl);
                    if (applicationUrl.isBlank()) applicationUrl = pageUrl;

                    SavedJob job = new SavedJob(userId, title, company, loc, applicationUrl, desc, "Structured Web Data");
                    job.setJobType(employmentType);
                    setImportMetadata(job, pageUrl, applicationUrl);

                    if (!datePosted.isBlank()) {
                        try { job.setPostedAt(Instant.parse(datePosted)); } catch (Exception ignored) {}
                    }

                    var skills = extractSkills(desc);
                    job.setRequiredSkills(skills.get("required"));
                    job.setPreferredSkills(skills.get("preferred"));

                    return job;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private JsonNode findJobPostingNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode child : node) { JsonNode found = findJobPostingNode(child); if (found != null) return found; }
            return null;
        }
        JsonNode type = node.path("@type");
        boolean posting = type.asText("").equalsIgnoreCase("JobPosting");
        if (type.isArray()) for (JsonNode item : type) posting |= item.asText("").equalsIgnoreCase("JobPosting");
        if (posting) return node;
        return node.has("@graph") ? findJobPostingNode(node.path("@graph")) : null;
    }

    private String structuredText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        if (value.isTextual() || value.isNumber()) return value.asText().trim();
        if (value.isArray()) {
            List<String> parts = new ArrayList<>();
            value.forEach(item -> { String text = structuredText(item); if (!text.isBlank()) parts.add(text); });
            return String.join("; ", parts);
        }
        if (value.isObject()) {
            for (String key : List.of("name", "description", "credentialCategory", "value")) {
                String text = structuredText(value.path(key));
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    // Helper: OpenGraph Meta tags parser
    private SavedJob parseMetaTagsJob(UUID userId, String pageUrl, String html) {
        String ogTitle = extractMetaContent(html, "og:title");
        String metaTitle = ogTitle != null ? ogTitle : extractTagContent(html, "title");
        String ogDesc = extractMetaContent(html, "og:description");
        if (ogDesc == null) ogDesc = extractMetaContent(html, "description");

        if (metaTitle == null || metaTitle.isBlank()) return null;

        String company = extractMetaContent(html, "og:site_name");
        if (company == null) company = "Online Opportunity";

        SavedJob job = new SavedJob(userId, metaTitle, company, "Unspecified", pageUrl, ogDesc != null ? ogDesc : "", "Web OpenGraph");
        setImportMetadata(job, pageUrl, pageUrl);
        var skills = extractSkills(ogDesc != null ? ogDesc : "");
        job.setRequiredSkills(skills.get("required"));
        job.setPreferredSkills(skills.get("preferred"));

        return job;
    }

    // Duplicate detection helper
    boolean isDuplicate(UUID userId, String source, String extId, String jobUrl, String title, String company) {
        if (extId != null && !extId.isBlank() && jobRepo.findByUserIdAndSourceAndExternalJobId(userId, source, extId).isPresent()) {
            return true;
        }
        if (jobUrl != null && !jobUrl.isBlank() && jobRepo.findByUserIdAndJobUrl(userId, jobUrl).isPresent()) {
            return true;
        }
        if (title != null && !title.isBlank() && company != null && !company.isBlank()
            && jobRepo.findByUserIdAndTitleIgnoreCaseAndCompanyIgnoreCase(userId, title, company).isPresent()) {
            return true;
        }
        return false;
    }

    // Skill separation helper (Required vs Preferred)
    public static Map<String, List<String>> extractSkills(String description) {
        if (description == null || description.isBlank()) {
            return Map.of("required", List.of(), "preferred", List.of());
        }

        String lower = description.toLowerCase(Locale.ROOT);
        int bonusIndex = -1;
        String[] preferredMarkers = {"preferred", "bonus", "nice to have", "plus", "desired", "advantageous"};
        for (String marker : preferredMarkers) {
            int idx = lower.indexOf(marker);
            if (idx != -1 && (bonusIndex == -1 || idx < bonusIndex)) {
                bonusIndex = idx;
            }
        }

        String requiredPart = bonusIndex != -1 ? description.substring(0, bonusIndex) : description;
        String preferredPart = bonusIndex != -1 ? description.substring(bonusIndex) : "";

        List<String> req = new ArrayList<>();
        List<String> pref = new ArrayList<>();

        for (String skill : COMMON_SKILLS) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(requiredPart).find()) {
                req.add(skill);
            } else if (!preferredPart.isBlank() && p.matcher(preferredPart).find()) {
                pref.add(skill);
            }
        }

        return Map.of("required", req, "preferred", pref);
    }

    // Security & Anti-SSRF check
    private void validateSafePublicUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Job URL cannot be empty.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim()).normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Job URL is invalid.");
        }
        String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only standard HTTP/HTTPS URLs are allowed.");
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null) {
            throw new IllegalArgumentException("Job URL must have a valid public host and cannot contain credentials.");
        }
        if ((uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
            || (scheme.equals("https") && uri.getPort() == 80)
            || (scheme.equals("http") && uri.getPort() == 443)) {
            throw new IllegalArgumentException("Job URL must use a standard web port.");
        }
        String host = uri.getHost();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        try {
            if (!host.contains(":")) host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Job URL host is invalid.");
        }
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isBlank() || host.equals("localhost") || host.endsWith(".localhost")
            || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test")) {
            throw new IllegalArgumentException("Internal / private network URLs are prohibited.");
        }
        if (host.equals("linkedin.com") || host.endsWith(".linkedin.com")) {
            String path = Objects.requireNonNullElse(uri.getPath(), "").toLowerCase(Locale.ROOT);
            if (path.startsWith("/jobs") || path.startsWith("/in")) {
                throw new IllegalArgumentException("LinkedIn requires candidate sign-in. To track a LinkedIn position, please enter the details manually or paste the description.");
            }
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isNonPublicAddress(address)) {
                    throw new IllegalArgumentException("Internal / private network URLs are prohibited.");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw new IllegalArgumentException("Job URL host could not be resolved.");
        }
    }

    private boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            long ip = ((bytes[0] & 0xffL) << 24) | ((bytes[1] & 0xffL) << 16)
                | ((bytes[2] & 0xffL) << 8) | (bytes[3] & 0xffL);
            long[][] blocked = {
                {0x00000000L, 8}, {0x64400000L, 10}, {0xc0000000L, 24}, {0xc0000200L, 24},
                {0xc0586300L, 24}, {0xc6120000L, 15}, {0xc6336400L, 24}, {0xcb007100L, 24},
                {0xf0000000L, 4}
            };
            for (long[] range : blocked) {
                int prefix = (int) range[1];
                long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
                if ((ip & mask) == (range[0] & mask)) return true;
            }
            return false;
        }
        if (bytes.length == 16) {
            if ((bytes[0] & 0xfe) == 0xfc) return true; // IPv6 unique-local fc00::/7
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) return true; // documentation range
            boolean mappedV4 = true;
            for (int i = 0; i < 10; i++) if (bytes[i] != 0) mappedV4 = false;
            if (mappedV4 && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                try { return isNonPublicAddress(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16))); }
                catch (java.net.UnknownHostException ignored) { return true; }
            }
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x02) return true; // 6to4 embeds IPv4
        }
        return false;
    }

    private String fetchHttp(String url) throws Exception {
        String currentUrl = url;
        for (int redirects = 0; redirects <= 3; redirects++) {
            validateSafePublicUrl(currentUrl);
            HttpRequest req = HttpRequest.newBuilder(URI.create(currentUrl))
                .header("User-Agent", "ApplyPilot-JobDiscovery/1.0 (+https://applypilot.ai)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status >= 300 && status < 400) {
                resp.body().close();
                String location = resp.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("Source returned a redirect without a destination."));
                if (redirects == 3) throw new IllegalStateException("Source redirected too many times.");
                currentUrl = URI.create(currentUrl).resolve(location).toString();
                continue;
            }
            if (status < 200 || status >= 300) {
                resp.body().close();
                throw new IllegalStateException("Public source returned an unsuccessful response.");
            }
            long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_RESPONSE_BYTES) {
                resp.body().close();
                throw new IllegalStateException("Public source response exceeds the size limit.");
            }
            try (InputStream body = resp.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("Public source response exceeds the size limit.");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Source redirected too many times.");
    }

    private void recordLog(UUID userId, String source, String url, String status, int imported, int skipped, String error) {
        try {
            logRepo.save(new JobIngestionLog(userId, source, sanitizedLogUrl(url), status, imported, skipped, error));
        } catch (Exception e) {
            log.warn("Could not save ingestion log: {}", e.getMessage());
        }
    }

    private String sanitizedLogUrl(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) return value;
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) { return "[invalid URL]"; }
    }

    private void setImportMetadata(SavedJob job, String sourceUrl, String applicationUrl) {
        Map<String, Object> metadata = new HashMap<>(job.getRawData());
        metadata.put("sourceUrl", sourceUrl);
        metadata.put("retrievedAt", Instant.now().toString());
        metadata.put("applicationUrl", applicationUrl == null || applicationUrl.isBlank() ? sourceUrl : applicationUrl);
        job.setRawData(metadata);
    }

    private String extractGreenhouseToken(String input) {
        if (!input.contains("/")) return input.trim();
        Matcher m = Pattern.compile("boards(?:-api)?\\.greenhouse\\.io/(?:v1/boards/)?([^/?#]+)").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractGreenhouseJobId(String input) {
        Matcher m = Pattern.compile("/jobs/(\\d+)").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractLeverSite(String input) {
        if (!input.contains("/")) return input.trim();
        Matcher m = Pattern.compile("(?:jobs\\.lever\\.co|api\\.lever\\.co/v0/postings)/([^/?#]+)").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractLeverPostingId(String input) {
        Matcher m = Pattern.compile("(?:jobs\\.lever\\.co|api\\.lever\\.co/v0/postings)/[^/]+/([a-f0-9\\-]+)").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    private String extractMetaContent(String html, String propertyName) {
        Pattern p = Pattern.compile("<meta[^>]+(?:name|property)=[\"']" + Pattern.quote(propertyName) + "[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private String extractTagContent(String html, String tag) {
        Pattern p = Pattern.compile("<" + Pattern.quote(tag) + "[^>]*>(.*?)</" + Pattern.quote(tag) + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private String extractCompanyFromTitle(String title) {
        if (title.contains(" at ")) {
            return title.substring(title.lastIndexOf(" at ") + 4).trim();
        }
        if (title.contains(" - ")) {
            return title.substring(title.lastIndexOf(" - ") + 3).trim();
        }
        return "Company";
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
