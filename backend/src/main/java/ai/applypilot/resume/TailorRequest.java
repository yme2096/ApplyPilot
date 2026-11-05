package ai.applypilot.resume;
import jakarta.validation.constraints.*;
public record TailorRequest(
    @NotBlank @Size(max=10000) String jobDescription,
    @NotBlank @Size(max=200) String targetRole
) {}
