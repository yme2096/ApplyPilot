package ai.applypilot.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {
    List<SavedJob> findAllByUserIdOrderBySavedAtDesc(UUID userId);
    List<SavedJob> findAllByUserIdAndStatusOrderBySavedAtDesc(UUID userId, String status);
    Optional<SavedJob> findByIdAndUserId(UUID id, UUID userId);
    long countByUserId(UUID userId);

    Optional<SavedJob> findByUserIdAndSourceAndExternalJobId(UUID userId, String source, String externalJobId);
    Optional<SavedJob> findByUserIdAndJobUrl(UUID userId, String jobUrl);
    Optional<SavedJob> findByUserIdAndTitleIgnoreCaseAndCompanyIgnoreCase(UUID userId, String title, String company);
}
