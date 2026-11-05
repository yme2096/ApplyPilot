package ai.applypilot.identity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="password_reset_tokens") public class PasswordResetTokenEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) UserEntity user;
 @Column(name="token_hash",nullable=false,unique=true) String tokenHash;
 @Column(name="expires_at",nullable=false) Instant expiresAt;
 @Column(name="consumed_at") Instant consumedAt;
 protected PasswordResetTokenEntity(){}
 public PasswordResetTokenEntity(UserEntity user,String tokenHash,Instant expiresAt){this.user=user;this.tokenHash=tokenHash;this.expiresAt=expiresAt;}
 public UserEntity getUser(){return user;} public boolean active(){return consumedAt==null&&expiresAt.isAfter(Instant.now());} public void consume(){consumedAt=Instant.now();}
}
