package ai.applypilot.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobIngestionLogRepository extends JpaRepository<JobIngestionLog, UUID> {
    List<JobIngestionLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
