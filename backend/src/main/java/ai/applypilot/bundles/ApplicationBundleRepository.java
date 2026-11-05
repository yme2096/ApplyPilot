package ai.applypilot.bundles;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationBundleRepository extends JpaRepository<ApplicationBundle, UUID> {
    List<ApplicationBundle> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<ApplicationBundle> findAllByUserIdAndJobIdOrderByCreatedAtDesc(UUID userId, UUID jobId);
    List<ApplicationBundle> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    Optional<ApplicationBundle> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);
    long countByUserId(UUID userId);
}
