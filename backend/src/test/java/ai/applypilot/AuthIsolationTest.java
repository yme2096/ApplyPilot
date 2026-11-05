package ai.applypilot;

import ai.applypilot.identity.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIsolationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private String base() { return "http://localhost:" + port; }

    private HttpHeaders bearer(String token) {
        var h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private String register(String email) {
        var body = new java.util.HashMap<String, String>();
        body.put("email", email);
        body.put("password", "SecurePassword123!");
        var resp = rest.postForEntity(base() + "/api/auth/register", body, AuthService.AuthResponse.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        return resp.getBody().accessToken();
    }

    @Test void unauthenticated_profile_returns_401_or_403() {
        var resp = rest.getForEntity(base() + "/api/profile", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).contains("UNAUTHORIZED");
    }

    @Test void unauthenticated_resumes_returns_401_or_403() {
        var resp = rest.getForEntity(base() + "/api/resumes", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).contains("UNAUTHORIZED");
    }

    @Test void unauthenticated_records_returns_401_or_403() {
        var resp = rest.getForEntity(base() + "/api/profile/records/education", String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test void authenticated_user_gets_own_empty_profile() {
        String token = register("alice_" + System.nanoTime() + "@test.com");
        var resp = rest.exchange(base() + "/api/profile", HttpMethod.GET,
            new HttpEntity<>(bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void authenticated_user_can_save_profile() {
        String token = register("bob_" + System.nanoTime() + "@test.com");
        var body = new java.util.HashMap<String, Object>();
        body.put("fullName", "Bob Test");
        body.put("phone", "1234567890");
        body.put("location", "Remote");
        body.put("preferredRoles", java.util.List.of("Engineer"));
        body.put("preferredLocations", java.util.List.of("Remote"));
        var resp = rest.exchange(base() + "/api/profile", HttpMethod.PUT,
            new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Bob Test");
    }

    @Test void two_users_each_see_only_own_profile() {
        String tokenA = register("userA_" + System.nanoTime() + "@test.com");
        String tokenB = register("userB_" + System.nanoTime() + "@test.com");

        // Save Alice's profile
        var bodyA = new java.util.HashMap<String, Object>();
        bodyA.put("fullName", "Alice Unique");
        bodyA.put("preferredRoles", java.util.List.of());
        bodyA.put("preferredLocations", java.util.List.of());
        rest.exchange(base() + "/api/profile", HttpMethod.PUT,
            new HttpEntity<>(bodyA, bearer(tokenA)), String.class);

        // Bob reads his own profile — must NOT contain Alice's name
        var respB = rest.exchange(base() + "/api/profile", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenB)), String.class);
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respB.getBody()).doesNotContain("Alice Unique");
    }

    @Test void invalid_token_returns_401_or_403() {
        var h = new HttpHeaders();
        h.setBearerAuth("totally.invalid.token");
        var resp = rest.exchange(base() + "/api/profile", HttpMethod.GET,
            new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).contains("UNAUTHORIZED");
    }

    @Test void unknown_record_kind_returns_404_with_valid_token() {
        String token = register("rectest_" + System.nanoTime() + "@test.com");
        var resp = rest.exchange(base() + "/api/profile/records/unknownkind", HttpMethod.GET,
            new HttpEntity<>(bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void resume_upload_bad_content_type_returns_400_or_415() {
        String token = register("upload_" + System.nanoTime() + "@test.com");
        // Send non-multipart body to the multipart endpoint
        var h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.exchange(base() + "/api/resumes", HttpMethod.POST,
            new HttpEntity<>("{}", h), String.class);
        assertThat(resp.getStatusCode().value()).isIn(400, 415);
    }

    @Test void password_reset_request_without_smtp_returns_503() {
        var body = new java.util.HashMap<String, String>();
        body.put("email", "any@test.com");
        var resp = rest.postForEntity(base() + "/api/auth/password-reset/request", body, String.class);
        // The service rejects unconfigured SMTP with a controlled 503. The /error dispatch
        // must retain that status instead of being replaced by an authentication 403.
        assertThat(resp.getStatusCode().value()).isEqualTo(503);
    }

    @Test void duplicate_registration_returns_409() {
        String email = "dup_" + System.nanoTime() + "@test.com";
        register(email);
        var body = new java.util.HashMap<String, String>();
        body.put("email", email);
        body.put("password", "SecurePassword123!");
        var resp = rest.postForEntity(base() + "/api/auth/register", body, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test void job_saving_and_user_isolation() {
        String tokenA = register("jobuserA_" + System.nanoTime() + "@test.com");
        String tokenB = register("jobuserB_" + System.nanoTime() + "@test.com");

        // User A saves a job
        var saveBody = new java.util.HashMap<String, Object>();
        saveBody.put("title", "Senior Backend Engineer");
        saveBody.put("company", "TechCorp");
        saveBody.put("location", "Remote");
        saveBody.put("description", "Requires Java, Spring Boot, PostgreSQL, Docker, and REST APIs.");
        saveBody.put("source", "Direct Application");

        var postResp = rest.exchange(base() + "/api/jobs", HttpMethod.POST,
            new HttpEntity<>(saveBody, bearer(tokenA)), ai.applypilot.jobs.JobController.JobDto.class);
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResp.getBody().title()).isEqualTo("Senior Backend Engineer");
        assertThat(postResp.getBody().matchScore()).isNotNull();

        var questions = rest.exchange(base() + "/api/jobs/" + postResp.getBody().id() + "/questions", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenA)), String.class);
        assertThat(questions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(questions.getBody()).contains("Java", "motivation");

        // User A lists jobs and sees it
        var listA = rest.exchange(base() + "/api/jobs", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenA)), String.class);
        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).contains("Senior Backend Engineer");

        // User B lists jobs and sees an empty list (isolation verified!)
        var listB = rest.exchange(base() + "/api/jobs", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenB)), String.class);
        assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listB.getBody()).doesNotContain("Senior Backend Engineer");
    }

    @Test void job_match_analysis_endpoint() {
        String token = register("analyzer_" + System.nanoTime() + "@test.com");
        var req = new java.util.HashMap<String, String>();
        req.put("title", "Java Engineer");
        req.put("location", "Remote");
        req.put("description", "Looking for Java and PostgreSQL experience.");

        var resp = rest.exchange(base() + "/api/jobs/analyze", HttpMethod.POST,
            new HttpEntity<>(req, bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("score");
        assertThat(resp.getBody()).contains("roleAlignment");
    }

    @Test void answer_draft_returns_controlled_response_when_local_ai_is_disabled() {
        String email = "answerdraft_" + System.nanoTime() + "@test.com";
        String token = register(email);
        java.util.UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", java.util.UUID.class, email);
        java.util.UUID resumeId = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO resumes(id,user_id,original_filename,storage_key,sha256,content_type,size_bytes) VALUES(?,?,?,?,?,?,?)",
            resumeId, userId, "evidence.pdf", "test/evidence.pdf", "0".repeat(64), "application/pdf", 10L);
        jdbc.update("INSERT INTO resume_versions(resume_id,version_number,storage_key,sha256,extracted_text,structured_data,evidence) VALUES(?,?,?,?,?,?,?)",
            resumeId, 1, "test/evidence.pdf", "0".repeat(64), "Built a parser using Java and Python", "{}", "[]");

        var evidence = java.util.Map.of("sourceType", "resume", "sourceId", resumeId.toString(),
            "version", 1, "excerpt", "Built a parser using Java and Python");
        var body = new java.util.HashMap<String, Object>();
        body.put("question", "Describe your parser experience");
        body.put("evidence", java.util.List.of(evidence));
        var resp = rest.exchange(base() + "/api/answer-library/draft", HttpMethod.POST,
            new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).contains("Local AI is disabled");
    }

    @Test void ingestion_anti_ssrf_and_linkedin_protection() {
        String token = register("ingestsec_" + System.nanoTime() + "@test.com");

        // Reject private addresses even when encoded as noncanonical IP literals.
        for (String unsafeUrl : java.util.List.of(
            "http://127.0.0.1:8080/admin",
            "http://2130706433/admin",
            "http://10.2.3.4/jobs",
            "http://[::1]/admin",
            "https://user@127.0.0.1/jobs",
            "https://company.internal/jobs"
        )) {
            var badReq = new java.util.HashMap<String, String>();
            badReq.put("urlOrToken", unsafeUrl);
            var badResp = rest.exchange(base() + "/api/jobs/import/url", HttpMethod.POST,
                new HttpEntity<>(badReq, bearer(token)), String.class);
            assertThat(badResp.getStatusCode()).as(unsafeUrl).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        // Attempt restricted LinkedIn scraping
        var liReq = new java.util.HashMap<String, String>();
        liReq.put("urlOrToken", "https://www.linkedin.com/jobs/view/123456");
        var liResp = rest.exchange(base() + "/api/jobs/import/url", HttpMethod.POST,
            new HttpEntity<>(liReq, bearer(token)), String.class);
        assertThat(liResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void application_bundle_creation_and_isolation() {
        String tokenA = register("bundleuserA_" + System.nanoTime() + "@test.com");
        String tokenB = register("bundleuserB_" + System.nanoTime() + "@test.com");

        // User A saves a job first
        var jobReq = new java.util.HashMap<String, Object>();
        jobReq.put("title", "Frontend Architect");
        jobReq.put("company", "DesignSystem Inc");
        var jobResp = rest.exchange(base() + "/api/jobs", HttpMethod.POST,
            new HttpEntity<>(jobReq, bearer(tokenA)), ai.applypilot.jobs.JobController.JobDto.class);
        assertThat(jobResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        java.util.UUID jobId = jobResp.getBody().id();

        // User A creates an application bundle for the job
        var bundleReq = new java.util.HashMap<String, Object>();
        bundleReq.put("jobId", jobId);
        bundleReq.put("notes", "Prepared initial candidate profile answers");
        bundleReq.put("applicationAnswers", java.util.List.of(
            java.util.Map.of("question", "Years of React experience", "answer", "5+ years", "category", "Frontend")
        ));

        var bResp = rest.exchange(base() + "/api/bundles", HttpMethod.POST,
            new HttpEntity<>(bundleReq, bearer(tokenA)), String.class);
        assertThat(bResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bResp.getBody()).contains("Frontend Architect");
        assertThat(bResp.getBody()).contains("DRAFT");
        assertThat(bResp.getBody()).contains("Years of React experience");

        // User A lists bundles
        var listA = rest.exchange(base() + "/api/bundles", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenA)), String.class);
        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).contains("Frontend Architect");

        // User B lists bundles - must be isolated (empty list)
        var listB = rest.exchange(base() + "/api/bundles", HttpMethod.GET,
            new HttpEntity<>(bearer(tokenB)), String.class);
        assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listB.getBody()).doesNotContain("Frontend Architect");
    }

    @Test void bulk_bundle_endpoint_requires_authentication_and_valid_batch() {
        var anonymous = rest.postForEntity(base() + "/api/bundles/bulk", java.util.Map.of("items", java.util.List.of()), String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = register("bulkuser_" + System.nanoTime() + "@test.com");
        var emptyBatch = rest.exchange(base() + "/api/bundles/bulk", HttpMethod.POST,
            new HttpEntity<>(java.util.Map.of("items", java.util.List.of()), bearer(token)), String.class);
        assertThat(emptyBatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emptyBatch.getBody()).contains("between 1 and 25 jobs");
    }
}
