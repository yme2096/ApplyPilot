package ai.applypilot.resume;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="resumes") public class ResumeEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @Column(name="user_id",nullable=false) UUID userId;
 @Column(name="original_filename",nullable=false) String filename; @Column(name="storage_key",nullable=false) String storageKey;
 @Column(nullable=false,length=64) String sha256; @Column(name="content_type",nullable=false) String contentType; @Column(name="size_bytes",nullable=false) long size;
 @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; protected ResumeEntity(){}
 public ResumeEntity(UUID user,String filename,String key,String hash,String type,long size){this.userId=user;this.filename=filename;this.storageKey=key;this.sha256=hash;this.contentType=type;this.size=size;}
 public UUID getId(){return id;} public String getFilename(){return filename;} public String getSha256(){return sha256;} public long getSize(){return size;} public Instant getCreatedAt(){return createdAt;} public String getStorageKey(){return storageKey;}
}
