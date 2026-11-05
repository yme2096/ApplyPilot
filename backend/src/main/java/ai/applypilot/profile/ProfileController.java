package ai.applypilot.profile;
import org.springframework.security.core.Authentication; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.bind.annotation.*; import jakarta.validation.Valid; import java.util.UUID;
@RestController @RequestMapping("/api/profile") public class ProfileController {
 private final ProfileRepository profiles; public ProfileController(ProfileRepository p){profiles=p;}
 private UUID owner(Authentication a){return (UUID)a.getPrincipal();}
 @GetMapping @Transactional public ProfileDto get(Authentication a){return profiles.findById(owner(a)).orElseGet(()->new ProfileEntity(owner(a))).dto();}
 @PutMapping @Transactional public ProfileDto put(Authentication a,@Valid @RequestBody ProfileDto d){var p=profiles.findById(owner(a)).orElseGet(()->new ProfileEntity(owner(a)));p.apply(d);return profiles.save(p).dto();}
}
