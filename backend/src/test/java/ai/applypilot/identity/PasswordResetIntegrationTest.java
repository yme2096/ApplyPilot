package ai.applypilot.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=smtp.test.local")
class PasswordResetIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mailSender;

    private String base() { return "http://localhost:" + port; }

    @Test void resetLinkChangesPasswordAndRejectsOldPasswordAndReplay() throws Exception {
        String email = "reset_" + UUID.randomUUID() + "@example.test";
        var registration = Map.of("email", email, "password", "OldPassword123!");
        assertThat(rest.postForEntity(base() + "/api/auth/register", registration, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        var unknown = rest.postForEntity(base() + "/api/auth/password-reset/request",
            Map.of("email", "unknown@example.test"), String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(mailSender);

        var requested = rest.postForEntity(base() + "/api/auth/password-reset/request",
            Map.of("email", email), String.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        var matcher = java.util.regex.Pattern.compile("#resetToken=([A-Za-z0-9_-]+)")
            .matcher(message.getValue().getText());
        assertThat(matcher.find()).isTrue();
        assertThat(message.getValue().getText()).doesNotContain("?resetToken=");
        String token = matcher.group(1);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class))
            .isEqualTo(hash).isNotEqualTo(token);

        var confirmBody = Map.of("token", token, "newPassword", "NewPassword123!");
        var confirmed = rest.postForEntity(base() + "/api/auth/password-reset/confirm", confirmBody, String.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var newLogin = rest.postForEntity(base() + "/api/auth/login",
            Map.of("email", email, "password", "NewPassword123!"), String.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        var oldLogin = rest.postForEntity(base() + "/api/auth/login",
            Map.of("email", email, "password", "OldPassword123!"), String.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var replay = rest.postForEntity(base() + "/api/auth/password-reset/confirm", confirmBody, String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
