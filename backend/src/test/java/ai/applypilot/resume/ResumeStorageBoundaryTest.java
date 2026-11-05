package ai.applypilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumeStorageBoundaryTest {
    @TempDir Path temporary;
    private ResumeRepository resumes;
    private ResumeVersionRepository versions;
    private ResumeService service;
    private final UUID owner = UUID.randomUUID();
    private final UUID resumeId = UUID.randomUUID();

    @BeforeEach void setUp() throws Exception {
        Path uploadRoot = Files.createDirectories(temporary.resolve("uploads"));
        resumes = mock(ResumeRepository.class);
        versions = mock(ResumeVersionRepository.class);
        service = new ResumeService(resumes, versions, uploadRoot.toString(), "http://worker.invalid", new ObjectMapper());
    }

    @Test void rejectsResumeStorageKeysThatEscapeUploadRoot() throws Exception {
        Path outside = temporary.resolve("outside.txt");
        Files.writeString(outside, "must not be returned");
        when(resumes.findByIdAndUserId(resumeId, owner)).thenReturn(Optional.of(
            new ResumeEntity(owner, "resume.pdf", "../outside.txt", "hash", "application/pdf", 18)));

        Throwable error = catchThrowable(() -> service.download(owner, resumeId));

        assertNotFound(error);
    }

    @Test void rejectsVersionStorageKeysThatEscapeUploadRoot() throws Exception {
        Path outside = temporary.resolve("outside.pdf");
        Files.writeString(outside, "%PDF-outside");
        when(resumes.findByIdAndUserId(resumeId, owner)).thenReturn(Optional.of(
            new ResumeEntity(owner, "resume.pdf", "owner/resume.pdf", "hash", "application/pdf", 12)));
        when(versions.findByResumeIdAndVersionNumber(resumeId, 1)).thenReturn(Optional.of(
            new ResumeVersionEntity(resumeId, 1, "../outside.pdf", "hash")));

        Throwable error = catchThrowable(() -> service.downloadVersion(owner, resumeId, 1));

        assertNotFound(error);
    }

    @Test void readsFilesWithinUploadRoot() throws Exception {
        Path file = temporary.resolve("uploads").resolve("owner/resume.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "%PDF-safe");
        when(resumes.findByIdAndUserId(resumeId, owner)).thenReturn(Optional.of(
            new ResumeEntity(owner, "resume.pdf", "owner/resume.pdf", "hash", "application/pdf", 9)));

        assertThat(new String(service.download(owner, resumeId))).isEqualTo("%PDF-safe");
    }

    private void assertNotFound(Throwable error) {
        assertThat(error).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
