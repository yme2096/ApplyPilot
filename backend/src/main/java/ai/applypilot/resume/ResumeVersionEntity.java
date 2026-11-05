package ai.applypilot.resume;
import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import java.time.Instant; import java.util.UUID; import java.util.Map; import java.util.List; import java.util.HashMap; import java.util.ArrayList;
@Entity @Table(name="resume_versions") public class ResumeVersionEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @Column(name="resume_id",nullable=false) UUID resumeId; @Column(name="version_number",nullable=false) int versionNumber;
 @Column(name="storage_key",nullable=false) String storageKey; @Column(nullable=false,length=64) String sha256;
 @Column(name="extracted_text",columnDefinition="text") String extractedText;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="structured_data",columnDefinition="jsonb",nullable=false) Map<String,Object> structuredData=new HashMap<>();
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="evidence",columnDefinition="jsonb",nullable=false) List<Object> evidence=new ArrayList<>();
 @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; protected ResumeVersionEntity(){}
 public ResumeVersionEntity(UUID resume,int version,String key,String hash){resumeId=resume;versionNumber=version;storageKey=key;sha256=hash;}
 public void applyParsed(String text,Map<String,Object> data,List<Object> proof){extractedText=text;structuredData=data;evidence=proof;}
 public int getVersionNumber(){return versionNumber;} public String getSha256(){return sha256;} public String getExtractedText(){return extractedText;} public Map<String,Object> getStructuredData(){return structuredData;} public List<Object> getEvidence(){return evidence;} public Instant getCreatedAt(){return createdAt;}
 public String getStorageKey(){return storageKey;}
}
