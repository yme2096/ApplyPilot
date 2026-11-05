package ai.applypilot.resume;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ResumeRepository extends JpaRepository<ResumeEntity,UUID>{List<ResumeEntity> findAllByUserIdOrderByCreatedAtDesc(UUID owner);Optional<ResumeEntity> findByIdAndUserId(UUID id,UUID owner);}
