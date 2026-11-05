package ai.applypilot.identity;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity,UUID>{Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);}
