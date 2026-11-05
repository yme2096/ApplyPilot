package ai.applypilot.bundles;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "application_bundles")
public class ApplicationBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "preparation_batch_id")
    private UUID preparationBatchId;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(name = "resume_version_number")
    private Integer resumeVersionNumber;

    @Column(name = "job_url", columnDefinition = "text")
    private String jobUrl;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT"; // DRAFT, READY, IN_REVIEW, SUBMITTED_BY_CANDIDATE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_answers", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> applicationAnswers = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_documents", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> requiredDocuments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications_snapshot", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> certificationsSnapshot = new ArrayList<>();

    @Column(name = "certifications_approved", nullable = false)
    private boolean certificationsApproved;

    @Column(name = "cover_letter_text", columnDefinition = "text")
    private String coverLetterText;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected ApplicationBundle() {}

    public ApplicationBundle(UUID userId, UUID jobId, UUID resumeId, Integer resumeVersionNumber, String jobUrl) {
        this(userId, jobId, resumeId, resumeVersionNumber, jobUrl, null);
    }

    public ApplicationBundle(UUID userId, UUID jobId, UUID resumeId, Integer resumeVersionNumber, String jobUrl, UUID preparationBatchId) {
        this.userId = userId;
        this.jobId = jobId;
        this.preparationBatchId = preparationBatchId;
        this.resumeId = resumeId;
        this.resumeVersionNumber = resumeVersionNumber;
        this.jobUrl = jobUrl;
        this.status = "DRAFT";
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getJobId() { return jobId; }
    public UUID getPreparationBatchId() { return preparationBatchId; }
    public UUID getResumeId() { return resumeId; }
    public Integer getResumeVersionNumber() { return resumeVersionNumber; }
    public String getJobUrl() { return jobUrl; }
    public String getStatus() { return status; }
    public List<Map<String, Object>> getApplicationAnswers() { return applicationAnswers != null ? applicationAnswers : Collections.emptyList(); }
    public List<Map<String, Object>> getRequiredDocuments() { return requiredDocuments != null ? requiredDocuments : Collections.emptyList(); }
    public List<Map<String, Object>> getCertificationsSnapshot() { return certificationsSnapshot != null ? certificationsSnapshot : Collections.emptyList(); }
    public boolean isCertificationsApproved() { return certificationsApproved; }
    public String getCoverLetterText() { return coverLetterText; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setResumeAttachment(UUID resumeId, Integer versionNumber) {
        this.resumeId = resumeId;
        this.resumeVersionNumber = versionNumber;
    }

    public void setStatus(String status) {
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
    }

    public void setApplicationAnswers(List<Map<String, Object>> answers) {
        this.applicationAnswers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
    }

    public void setRequiredDocuments(List<Map<String, Object>> documents) {
        this.requiredDocuments = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public void setCertificationsSnapshot(List<Map<String,Object>> snapshot) {
        this.certificationsSnapshot = snapshot != null ? new ArrayList<>(snapshot) : new ArrayList<>();
        this.certificationsApproved = this.certificationsSnapshot.isEmpty();
    }

    public void setCertificationsApproved(Boolean approved) {
        if (approved != null) this.certificationsApproved = approved;
    }

    public void setCoverLetterText(String coverLetterText) {
        this.coverLetterText = coverLetterText;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }
}
