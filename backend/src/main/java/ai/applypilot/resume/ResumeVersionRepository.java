package ai.applypilot.resume;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ResumeVersionRepository extends JpaRepository<ResumeVersionEntity,UUID>{List<ResumeVersionEntity> findAllByResumeIdOrderByVersionNumberDesc(UUID resumeId);java.util.Optional<ResumeVersionEntity> findFirstByResumeIdOrderByVersionNumberDesc(UUID resumeId);java.util.Optional<ResumeVersionEntity> findByResumeIdAndVersionNumber(UUID resumeId,int versionNumber);}
