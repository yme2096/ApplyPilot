package ai.applypilot.jobs;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_ingestion_logs")
public class JobIngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(nullable = false, length = 40)
    private String status; // SUCCESS, FAILED, PARTIAL

    @Column(name = "jobs_imported", nullable = false)
    private int jobsImported = 0;

    @Column(name = "jobs_skipped", nullable = false)
    private int jobsSkipped = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected JobIngestionLog() {}

    public JobIngestionLog(UUID userId, String source, String sourceUrl, String status, int imported, int skipped, String error) {
        this.userId = userId;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.status = status;
        this.jobsImported = imported;
        this.jobsSkipped = skipped;
        this.errorMessage = error;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getStatus() { return status; }
    public int getJobsImported() { return jobsImported; }
    public int getJobsSkipped() { return jobsSkipped; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
