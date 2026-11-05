package ai.applypilot.identity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="users") public class UserEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
 @Column(nullable=false,unique=true) String email;
 @Column(name="password_hash",nullable=false) String passwordHash;
 @Column(name="created_at",insertable=false,updatable=false) Instant createdAt;
 protected UserEntity() {}
 public UserEntity(String email,String passwordHash){this.email=email;this.passwordHash=passwordHash;}
 public UUID getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;}
 public void changePassword(String passwordHash){this.passwordHash=passwordHash;}
}
