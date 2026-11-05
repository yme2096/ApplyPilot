package ai.applypilot.bundles;

import ai.applypilot.jobs.SavedJob;
import ai.applypilot.jobs.SavedJobRepository;
import ai.applypilot.resume.ResumeRepository;
import ai.applypilot.resume.ResumeVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BundleServiceBulkTest {
    @Mock ApplicationBundleRepository bundleRepo;
    @Mock SavedJobRepository jobRepo;
    @Mock ResumeRepository resumeRepo;
    @Mock ResumeVersionRepository versionRepo;
    @InjectMocks BundleService service;

    @Test void bulk_preparation_creates_separate_drafts_per_job_with_selected_versions() {
        UUID owner = UUID.randomUUID();
        UUID resume = UUID.randomUUID();
        UUID jobA = UUID.randomUUID();
        UUID jobB = UUID.randomUUID();
        for (UUID jobId : List.of(jobA, jobB)) {
            SavedJob job = new SavedJob(owner, "Engineer", "Example", "Remote", "https://jobs.example/app", "Description", "Manual");
            when(jobRepo.findByIdAndUserId(jobId, owner)).thenReturn(Optional.of(job));
            when(bundleRepo.existsByUserIdAndJobId(owner, jobId)).thenReturn(false);
        }
        when(resumeRepo.findByIdAndUserId(resume, owner)).thenReturn(Optional.of(mock(ai.applypilot.resume.ResumeEntity.class)));
        when(versionRepo.findByResumeIdAndVersionNumber(resume, 2)).thenReturn(Optional.of(mock(ai.applypilot.resume.ResumeVersionEntity.class)));
        when(versionRepo.findByResumeIdAndVersionNumber(resume, 5)).thenReturn(Optional.of(mock(ai.applypilot.resume.ResumeVersionEntity.class)));
        when(bundleRepo.save(any(ApplicationBundle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createBulk(owner, new BundleService.BulkCreateRequest(List.of(
            new BundleService.CreateBundleRequest(jobA, resume, 2, null, null, null, List.of()),
            new BundleService.CreateBundleRequest(jobB, resume, 5, null, null, null, List.of())
        )));

        assertThat(created).hasSize(2);
        verify(bundleRepo, times(2)).save(any(ApplicationBundle.class));
    }

    @Test void bulk_preparation_rejects_existing_job_without_creating_any_bundles() {
        UUID owner = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        when(bundleRepo.existsByUserIdAndJobId(owner, job)).thenReturn(true);
        var item = new BundleService.CreateBundleRequest(job, UUID.randomUUID(), 1, null, null, null, List.of());

        assertThatThrownBy(() -> service.createBulk(owner, new BundleService.BulkCreateRequest(List.of(item))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("preparation already exists");
        verify(bundleRepo, never()).save(any());
    }

    @Test void bulk_preparation_rejects_duplicate_or_incomplete_items() {
        UUID owner = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        var first = new BundleService.CreateBundleRequest(job, UUID.randomUUID(), 1, null, null, null, List.of());
        assertThatThrownBy(() -> service.createBulk(owner, new BundleService.BulkCreateRequest(List.of(first, first))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("distinct saved job");
        var missingVersion = new BundleService.CreateBundleRequest(job, UUID.randomUUID(), null, null, null, null, List.of());
        assertThatThrownBy(() -> service.createBulk(owner, new BundleService.BulkCreateRequest(List.of(missingVersion))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("exact resume version");
        verify(bundleRepo, never()).save(any());
    }

    @Test void batch_bundle_cannot_be_marked_ready_without_resume_version_approval() {
        UUID owner = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        var groupedDraft = new ApplicationBundle(owner, UUID.randomUUID(), UUID.randomUUID(), 4, null, UUID.randomUUID());
        when(bundleRepo.findByIdAndUserId(bundleId, owner)).thenReturn(Optional.of(groupedDraft));
        var update = new BundleService.UpdateBundleRequest(null, null, null, "READY", null, null, null, null, false);

        assertThatThrownBy(() -> service.updateBundle(owner, bundleId, update))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Confirm review of this job's exact resume version");
        verify(bundleRepo, never()).save(any());
    }
}
