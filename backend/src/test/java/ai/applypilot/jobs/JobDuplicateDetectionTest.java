package ai.applypilot.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobDuplicateDetectionTest {
    private final SavedJobRepository jobs = mock(SavedJobRepository.class);
    private final JobIngestionService service = new JobIngestionService(jobs, mock(JobIngestionLogRepository.class), mock(JobMatchService.class), new ObjectMapper());
    private final UUID owner = UUID.randomUUID();

    @Test
    void detectsDuplicateBySourceIdOrCanonicalPostingUrl() {
        when(jobs.findByUserIdAndSourceAndExternalJobId(owner, "Greenhouse", "42")).thenReturn(Optional.of(mock(SavedJob.class)));
        assertThat(service.isDuplicate(owner, "Greenhouse", "42", null, "Engineer", "Example")).isTrue();

        when(jobs.findByUserIdAndJobUrl(owner, "https://jobs.example.org/42")).thenReturn(Optional.of(mock(SavedJob.class)));
        assertThat(service.isDuplicate(owner, "RSS Feed", null, "https://jobs.example.org/42", "Engineer", "Example")).isTrue();
    }

    @Test
    void detectsDuplicateByCaseInsensitiveTitleAndCompany() {
        when(jobs.findByUserIdAndTitleIgnoreCaseAndCompanyIgnoreCase(owner, "Backend Engineer", "Example Co"))
            .thenReturn(Optional.of(mock(SavedJob.class)));
        assertThat(service.isDuplicate(owner, "Manual Entry", null, null, "Backend Engineer", "Example Co")).isTrue();
    }

    @Test
    void ignoresEmptyIdentifiersAndKeepsNewJobs() {
        assertThat(service.isDuplicate(owner, "RSS Feed", "", "", "New role", "Example")).isFalse();
        verify(jobs, never()).findByUserIdAndSourceAndExternalJobId(any(), any(), any());
        verify(jobs, never()).findByUserIdAndJobUrl(any(), any());
        verify(jobs).findByUserIdAndTitleIgnoreCaseAndCompanyIgnoreCase(owner, "New role", "Example");
    }
}
