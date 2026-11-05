package ai.applypilot.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StructuredJobPostingImportTest {
    private final JobIngestionService service = new JobIngestionService(
        mock(SavedJobRepository.class), mock(JobIngestionLogRepository.class), mock(JobMatchService.class), new ObjectMapper());

    @Test
    void extractsJobPostingCompanyLocationAndQualificationRequirements() {
        String html = """
            <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":["WebPage"]},{"@type":["JobPosting"],"identifier":{"value":"job-42"},"title":"Backend Engineer","description":"Build services with Java.","hiringOrganization":{"@type":"Organization","name":"Example Systems"},"jobLocation":{"address":{"addressLocality":"Pune","addressRegion":"Maharashtra","addressCountry":{"name":"India"}}},"employmentType":"FULL_TIME","datePosted":"2026-10-30T00:00:00Z","educationRequirements":{"credentialCategory":"Bachelor's degree"},"experienceRequirements":"Two years relevant experience"}]}</script>
            """;
        var job = service.parseJsonLdJob(UUID.randomUUID(), "https://jobs.example.org/backend", html);

        assertThat(job).isNotNull();
        assertThat(job.getTitle()).isEqualTo("Backend Engineer");
        assertThat(job.getCompany()).isEqualTo("Example Systems");
        assertThat(job.getLocation()).isEqualTo("Pune, Maharashtra, India");
        assertThat(job.getDescription()).contains("Bachelor's degree", "Two years relevant experience");
        assertThat(job.getPostedAt()).isNotNull();
        assertThat(job.getRawData()).containsKeys("sourceUrl", "retrievedAt", "applicationUrl");
    }

    @Test
    void malformedOrUnrelatedStructuredDataDoesNotInventAJob() {
        assertThat(service.parseJsonLdJob(UUID.randomUUID(), "https://jobs.example.org/a",
            "<script type='application/ld+json'>{bad-json}</script>")).isNull();
        assertThat(service.parseJsonLdJob(UUID.randomUUID(), "https://jobs.example.org/a",
            "<script type='application/ld+json'>{\"@type\":\"Organization\",\"name\":\"Example\"}</script>")).isNull();
    }

    @Test
    void storesSourceRetrievalTimeAndDistinctApplicationUrl() {
        String html = """
            <script type="application/ld+json">{"@type":"JobPosting","title":"Data Engineer","url":"https://jobs.example.org/role","applicationUrl":"https://apply.example.org/role","hiringOrganization":{"name":"Example"},"description":"Build data pipelines."}</script>
            """;
        var job = service.parseJsonLdJob(UUID.randomUUID(), "https://jobs.example.org/role", html);

        assertThat(job).isNotNull();
        assertThat(job.getJobUrl()).isEqualTo("https://apply.example.org/role");
        assertThat(job.getRawData()).containsEntry("sourceUrl", "https://jobs.example.org/role")
            .containsEntry("applicationUrl", "https://apply.example.org/role");
        assertThat(java.time.Instant.parse((String) job.getRawData().get("retrievedAt"))).isNotNull();
    }
}
