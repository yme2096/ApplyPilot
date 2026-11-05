package ai.applypilot.identity;
import jakarta.validation.Valid; import jakarta.validation.constraints.Email; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController {
 private final AuthService service; private final PasswordResetService passwordReset; public AuthController(AuthService service,PasswordResetService passwordReset){this.service=service;this.passwordReset=passwordReset;}
 public record Credentials(@NotBlank @Email String email,@NotBlank @Size(min=12,max=128) String password){}
 public record Refresh(@NotBlank String refreshToken){}
 public record ResetRequest(@NotBlank @Email String email){}
 public record ResetConfirm(@NotBlank String token,@NotBlank @Size(min=12,max=128) String newPassword){}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public AuthService.AuthResponse register(@Valid @RequestBody Credentials c){return service.register(c.email(),c.password());}
 @PostMapping("/login") public AuthService.AuthResponse login(@Valid @RequestBody Credentials c){return service.login(c.email(),c.password());}
 @PostMapping("/refresh") public AuthService.AuthResponse refresh(@Valid @RequestBody Refresh r){return service.rotate(r.refreshToken());}
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody Refresh r){service.logout(r.refreshToken());}
 @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED) public void requestReset(@Valid @RequestBody ResetRequest r){passwordReset.request(r.email());}
 @PostMapping("/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT) public void confirmReset(@Valid @RequestBody ResetConfirm r){passwordReset.confirm(r.token(),r.newPassword());}
}
