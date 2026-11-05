package ai.applypilot.profile;
import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import java.time.Instant; import java.util.*;
@Entity @Table(name="user_profiles") public class ProfileEntity {
 @Id @Column(name="user_id") UUID userId;
 @Column(name="full_name") String fullName; String phone; String location;
 @Column(name="graduation_year") Integer graduationYear;
 @Column(name="linkedin_url") String linkedinUrl; @Column(name="github_url") String githubUrl; @Column(name="portfolio_url") String portfolioUrl;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="preferred_roles",columnDefinition="jsonb") List<String> preferredRoles=new ArrayList<>();
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="preferred_locations",columnDefinition="jsonb") List<String> preferredLocations=new ArrayList<>();
 @Column(name="job_type") String jobType; @Column(name="salary_preference") String salaryPreference;
 @Column(name="updated_at",insertable=false,updatable=false) Instant updatedAt;
 protected ProfileEntity(){} public ProfileEntity(UUID id){userId=id;}
 public ProfileDto dto(){return new ProfileDto(fullName,phone,location,graduationYear,linkedinUrl,githubUrl,portfolioUrl,preferredRoles,preferredLocations,jobType,salaryPreference);}
 public void apply(ProfileDto d){fullName=d.fullName();phone=d.phone();location=d.location();graduationYear=d.graduationYear();linkedinUrl=d.linkedinUrl();githubUrl=d.githubUrl();portfolioUrl=d.portfolioUrl();preferredRoles=d.preferredRoles()==null?new ArrayList<>():d.preferredRoles();preferredLocations=d.preferredLocations()==null?new ArrayList<>():d.preferredLocations();jobType=d.jobType();salaryPreference=d.salaryPreference();}
}
