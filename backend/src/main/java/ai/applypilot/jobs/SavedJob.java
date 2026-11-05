package ai.applypilot.jobs;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "saved_jobs")
public class SavedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    private String company;
    private String location;

    @Column(name = "job_url", columnDefinition = "text")
    private String jobUrl;

    @Column(columnDefinition = "text")
    private String description;

    private String source;

    @Column(name = "external_job_id")
    private String externalJobId;

    @Column(name = "job_type")
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_skills", columnDefinition = "jsonb", nullable = false)
    private List<String> preferredSkills = new ArrayList<>();

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawData = new HashMap<>();

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private String status = "saved";

    @Column(name = "saved_at", insertable = false, updatable = false)
    private Instant savedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected SavedJob() {}

    public SavedJob(UUID userId, String title, String company, String location, String jobUrl, String description, String source) {
        this.userId = userId;
        this.title = title;
        this.company = company;
        this.location = location;
        this.jobUrl = jobUrl;
        this.description = description;
        this.source = source != null && !source.isBlank() ? source : "Manual Entry";
        this.status = "saved";
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public String getJobUrl() { return jobUrl; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public String getExternalJobId() { return externalJobId; }
    public String getJobType() { return jobType; }
    public List<String> getRequiredSkills() { return requiredSkills != null ? requiredSkills : Collections.emptyList(); }
    public List<String> getPreferredSkills() { return preferredSkills != null ? preferredSkills : Collections.emptyList(); }
    public Instant getPostedAt() { return postedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Map<String, Object> getRawData() { return rawData != null ? rawData : Collections.emptyMap(); }
    public BigDecimal getMatchScore() { return matchScore; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public Instant getSavedAt() { return savedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills != null ? new ArrayList<>(requiredSkills) : new ArrayList<>(); }
    public void setPreferredSkills(List<String> preferredSkills) { this.preferredSkills = preferredSkills != null ? new ArrayList<>(preferredSkills) : new ArrayList<>(); }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
    public void setRawData(Map<String, Object> rawData) { this.rawData = rawData != null ? new HashMap<>(rawData) : new HashMap<>(); }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }

    public void update(String title, String company, String location, String notes, String status) {
        if (title != null && !title.isBlank()) this.title = title;
        if (company != null) this.company = company;
        if (location != null) this.location = location;
        if (notes != null) this.notes = notes;
        if (status != null && !status.isBlank()) this.status = status;
    }
}
