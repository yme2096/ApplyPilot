package ai.applypilot.profile;
import jakarta.validation.constraints.*; import java.util.List;
public record ProfileDto(@Size(max=160) String fullName,@Size(max=40) String phone,@Size(max=160) String location,@Min(1950) @Max(2100) Integer graduationYear,@Size(max=500) String linkedinUrl,@Size(max=500) String githubUrl,@Size(max=500) String portfolioUrl,List<@Size(max=120) String> preferredRoles,List<@Size(max=120) String> preferredLocations,@Size(max=40) String jobType,@Size(max=120) String salaryPreference){}
