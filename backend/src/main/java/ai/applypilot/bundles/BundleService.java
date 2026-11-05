package ai.applypilot.bundles;

import ai.applypilot.jobs.SavedJob;
import ai.applypilot.jobs.SavedJobRepository;
import ai.applypilot.resume.ResumeRepository;
import ai.applypilot.resume.ResumeVersionRepository;
import ai.applypilot.certifications.CertificationVaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class BundleService {

    private final ApplicationBundleRepository bundleRepo;
    private final SavedJobRepository jobRepo;
    private final ResumeRepository resumeRepo;
    private final ResumeVersionRepository versionRepo;
    private final CertificationVaultService certificationVault;

    @Autowired
    public BundleService(ApplicationBundleRepository bundleRepo,
                         SavedJobRepository jobRepo,
                         ResumeRepository resumeRepo,
                         ResumeVersionRepository versionRepo,
                         CertificationVaultService certificationVault) {
        this.bundleRepo = bundleRepo;
        this.jobRepo = jobRepo;
        this.resumeRepo = resumeRepo;
        this.versionRepo = versionRepo;
        this.certificationVault = certificationVault;
    }

    public BundleService(ApplicationBundleRepository bundleRepo, SavedJobRepository jobRepo,
                         ResumeRepository resumeRepo, ResumeVersionRepository versionRepo) {
        this(bundleRepo, jobRepo, resumeRepo, versionRepo, null);
    }

    public record BundleDto(
        UUID id,
        UUID preparationBatchId,
        UUID jobId,
        String jobTitle,
        String company,
        String jobUrl,
        UUID resumeId,
        Integer resumeVersionNumber,
        String resumeDownloadUrl,
        String status,
        List<Map<String, Object>> applicationAnswers,
        List<Map<String, Object>> requiredDocuments,
        List<Map<String, Object>> certificationsSnapshot,
        boolean certificationsApproved,
        String coverLetterText,
        String notes,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static BundleDto from(ApplicationBundle b, SavedJob job, String downloadUrl) {
            return new BundleDto(
                b.getId(),
                b.getPreparationBatchId(),
                b.getJobId(),
                job != null ? job.getTitle() : "Position",
                job != null ? job.getCompany() : "",
                b.getJobUrl() != null ? b.getJobUrl() : (job != null ? job.getJobUrl() : null),
                b.getResumeId(),
                b.getResumeVersionNumber(),
                downloadUrl,
                b.getStatus(),
                b.getApplicationAnswers(),
                b.getRequiredDocuments(),
                b.getCertificationsSnapshot(),
                b.isCertificationsApproved(),
                b.getCoverLetterText(),
                b.getNotes(),
                b.getCreatedAt(),
                b.getUpdatedAt()
            );
        }
    }

    public record CreateBundleRequest(
        UUID jobId,
        UUID resumeId,
        Integer resumeVersionNumber,
        String jobUrl,
        String coverLetterText,
        String notes,
        List<Map<String, Object>> applicationAnswers,
        List<UUID> certificationIds
    ) {
        public CreateBundleRequest(UUID jobId, UUID resumeId, Integer resumeVersionNumber, String jobUrl, String coverLetterText, String notes, List<Map<String,Object>> applicationAnswers) {
            this(jobId,resumeId,resumeVersionNumber,jobUrl,coverLetterText,notes,applicationAnswers,null);
        }
    }

    public record UpdateBundleRequest(
        UUID resumeId,
        Integer resumeVersionNumber,
        String jobUrl,
        String status,
        List<Map<String, Object>> applicationAnswers,
        List<Map<String, Object>> requiredDocuments,
        String coverLetterText,
        String notes,
        Boolean resumeVersionApproved,
        List<UUID> certificationIds,
        Boolean certificationsApproved
    ) {
        public UpdateBundleRequest(UUID resumeId,Integer resumeVersionNumber,String jobUrl,String status,List<Map<String,Object>> applicationAnswers,List<Map<String,Object>> requiredDocuments,String coverLetterText,String notes,Boolean resumeVersionApproved) {
            this(resumeId,resumeVersionNumber,jobUrl,status,applicationAnswers,requiredDocuments,coverLetterText,notes,resumeVersionApproved,null,null);
        }
    }

    public record BulkCreateRequest(List<CreateBundleRequest> items) {}

    public record ScreeningAnswer(
        String question,
        String answer,
        String category
    ) {}

    @Transactional
    public BundleDto createBundle(UUID userId, CreateBundleRequest req) {
        return createBundle(userId, req, null);
    }

    private BundleDto createBundle(UUID userId, CreateBundleRequest req, UUID preparationBatchId) {
        SavedJob job = jobRepo.findByIdAndUserId(req.jobId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target job opportunity not found."));

        if (req.resumeId() != null) {
            resumeRepo.findByIdAndUserId(req.resumeId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Specified resume not found."));
            if (req.resumeVersionNumber() != null) {
                versionRepo.findByResumeIdAndVersionNumber(req.resumeId(), req.resumeVersionNumber())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Specified resume version not found."));
            }
        }

        String jobUrl = req.jobUrl() != null && !req.jobUrl().isBlank() ? req.jobUrl() : job.getJobUrl();
        ApplicationBundle bundle = new ApplicationBundle(userId, job.getId(), req.resumeId(), req.resumeVersionNumber(), jobUrl, preparationBatchId);
        bundle.setCertificationsSnapshot(certificationSnapshot(userId, req.certificationIds()));

        if (req.coverLetterText() != null) bundle.setCoverLetterText(req.coverLetterText());
        if (req.notes() != null) bundle.setNotes(req.notes());
        if (req.applicationAnswers() != null) bundle.setApplicationAnswers(req.applicationAnswers());

        ApplicationBundle saved = bundleRepo.save(bundle);
        return toDto(saved, job);
    }

    @Transactional
    public List<BundleDto> createBulk(UUID userId, BulkCreateRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 25) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk preparation requires between 1 and 25 jobs.");
        }
        Set<UUID> jobIds = new HashSet<>();
        for (CreateBundleRequest item : request.items()) {
            if (item == null || item.jobId() == null || !jobIds.add(item.jobId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each bulk item must use a distinct saved job.");
            }
            if (item.resumeId() == null || item.resumeVersionNumber() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an exact resume version for every bulk job.");
            }
            if (bundleRepo.existsByUserIdAndJobId(userId, item.jobId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A preparation already exists for one or more selected jobs.");
            }
        }
        UUID batchId = UUID.randomUUID();
        return request.items().stream().map(item -> createBundle(userId, item, batchId)).toList();
    }

    @Transactional(readOnly = true)
    public List<BundleDto> listBundles(UUID userId, UUID jobId, String status) {
        List<ApplicationBundle> list;
        if (jobId != null) {
            list = bundleRepo.findAllByUserIdAndJobIdOrderByCreatedAtDesc(userId, jobId);
        } else if (status != null && !status.isBlank()) {
            list = bundleRepo.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            list = bundleRepo.findAllByUserIdOrderByCreatedAtDesc(userId);
        }

        return list.stream().map(b -> {
            SavedJob job = jobRepo.findById(b.getJobId()).orElse(null);
            return toDto(b, job);
        }).toList();
    }

    @Transactional(readOnly = true)
    public BundleDto getBundle(UUID userId, UUID bundleId) {
        ApplicationBundle bundle = bundleRepo.findByIdAndUserId(bundleId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application bundle not found."));
        SavedJob job = jobRepo.findById(bundle.getJobId()).orElse(null);
        return toDto(bundle, job);
    }

    @Transactional
    public BundleDto updateBundle(UUID userId, UUID bundleId, UpdateBundleRequest req) {
        ApplicationBundle bundle = bundleRepo.findByIdAndUserId(bundleId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application bundle not found."));

        if ("READY".equals(req.status()) && bundle.getPreparationBatchId() != null && !Boolean.TRUE.equals(req.resumeVersionApproved())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm review of this job's exact resume version before marking it READY.");
        }
        boolean includesCertifications = req.certificationIds() != null
            ? !req.certificationIds().isEmpty()
            : !bundle.getCertificationsSnapshot().isEmpty();
        if (Set.of("READY", "SUBMITTED_BY_CANDIDATE").contains(req.status()) && includesCertifications && !Boolean.TRUE.equals(req.certificationsApproved()) && !bundle.isCertificationsApproved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review and approve the selected certification snapshot before marking this bundle READY.");
        }

        if (req.resumeId() != null) {
            resumeRepo.findByIdAndUserId(req.resumeId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found."));
            if (req.resumeVersionNumber() != null) {
                versionRepo.findByResumeIdAndVersionNumber(req.resumeId(), req.resumeVersionNumber())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found."));
            }
            bundle.setResumeAttachment(req.resumeId(), req.resumeVersionNumber());
        }

        if (req.status() != null && !req.status().isBlank()) {
            validateStatus(req.status());
            bundle.setStatus(req.status());
        }

        if (req.jobUrl() != null) bundle.setJobUrl(req.jobUrl());
        if (req.coverLetterText() != null) bundle.setCoverLetterText(req.coverLetterText());
        if (req.notes() != null) bundle.setNotes(req.notes());
        if (req.applicationAnswers() != null) bundle.setApplicationAnswers(req.applicationAnswers());
        if (req.requiredDocuments() != null) bundle.setRequiredDocuments(req.requiredDocuments());
        if (req.certificationIds() != null) bundle.setCertificationsSnapshot(certificationSnapshot(userId, req.certificationIds()));
        if (req.certificationsApproved() != null) bundle.setCertificationsApproved(req.certificationsApproved());

        ApplicationBundle updated = bundleRepo.save(bundle);
        SavedJob job = jobRepo.findById(updated.getJobId()).orElse(null);
        return toDto(updated, job);
    }

    @Transactional
    public BundleDto addScreeningAnswer(UUID userId, UUID bundleId, ScreeningAnswer answer) {
        ApplicationBundle bundle = bundleRepo.findByIdAndUserId(bundleId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application bundle not found."));

        List<Map<String, Object>> current = new ArrayList<>(bundle.getApplicationAnswers());
        // Replace or append
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            if (answer.question().equalsIgnoreCase(String.valueOf(current.get(i).get("question")))) {
                current.set(i, Map.of(
                    "question", answer.question(),
                    "answer", answer.answer(),
                    "category", answer.category() != null ? answer.category() : "General"
                ));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            current.add(Map.of(
                "question", answer.question(),
                "answer", answer.answer(),
                "category", answer.category() != null ? answer.category() : "General"
            ));
        }

        bundle.setApplicationAnswers(current);
        ApplicationBundle saved = bundleRepo.save(bundle);
        SavedJob job = jobRepo.findById(saved.getJobId()).orElse(null);
        return toDto(saved, job);
    }

    @Transactional
    public void deleteBundle(UUID userId, UUID bundleId) {
        ApplicationBundle bundle = bundleRepo.findByIdAndUserId(bundleId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application bundle not found."));
        bundleRepo.delete(bundle);
    }

    private BundleDto toDto(ApplicationBundle bundle, SavedJob job) {
        String downloadUrl = null;
        if (bundle.getResumeId() != null) {
            if (bundle.getResumeVersionNumber() != null) {
                downloadUrl = "/api/resumes/" + bundle.getResumeId() + "/versions/" + bundle.getResumeVersionNumber() + "/download";
            } else {
                downloadUrl = "/api/resumes/" + bundle.getResumeId() + "/download";
            }
        }
        return BundleDto.from(bundle, job, downloadUrl);
    }

    private void validateStatus(String status) {
        Set<String> valid = Set.of("DRAFT", "READY", "IN_REVIEW", "SUBMITTED_BY_CANDIDATE");
        if (!valid.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preparation status: " + status);
        }
    }

    private List<Map<String,Object>> certificationSnapshot(UUID userId, List<UUID> certificationIds) {
        if (certificationIds == null || certificationIds.isEmpty()) return List.of();
        if (certificationVault == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Certification selection is unavailable.");
        return certificationVault.selected(userId, certificationIds).snapshot();
    }
}
