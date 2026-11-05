package ai.applypilot.certifications;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CertificationRulesTest {
    @Test void accepts_issuer_and_credly_https_links_but_rejects_plain_http_and_embedded_credentials() {
        assertThat(CertificationRules.httpsUrl("https://issuer.example/verify?id=42", "URL")).isEqualTo("https://issuer.example/verify?id=42");
        assertThatThrownBy(() -> CertificationRules.httpsUrl("http://issuer.example/verify", "URL"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CertificationRules.httpsUrl("https://user:secret@issuer.example/verify", "URL"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejects_invalid_date_order_and_future_issue_date() {
        assertThatThrownBy(() -> CertificationRules.validateDates(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-03-31")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CertificationRules.validateDates(LocalDate.now().plusDays(1), null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void defaults_and_validates_custom_reminder_intervals() {
        assertThat(CertificationRules.reminderDays(null)).containsExactly(90, 30, 7);
        assertThat(CertificationRules.reminderDays(List.of(7, 30))).containsExactly(30, 7);
        assertThatThrownBy(() -> CertificationRules.reminderDays(List.of(30, 30))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CertificationRules.reminderDays(List.of(366))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void distinguishes_no_expiry_and_expired_credentials() {
        assertThat(CertificationVaultService.expirationStatus(null)).isEqualTo("NO_EXPIRY");
        assertThat(CertificationVaultService.expirationStatus(LocalDate.now().minusDays(1))).isEqualTo("EXPIRED");
        assertThat(CertificationVaultService.expirationStatus(LocalDate.now().plusDays(10))).isEqualTo("EXPIRING_SOON");
        assertThat(CertificationVaultService.expirationStatus(LocalDate.now().plusDays(60))).isEqualTo("ACTIVE");
    }

    @Test void pdf_validation_requires_header_eof_and_size_limit() {
        byte[] valid = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(CertificatePdfValidator.isValid(valid, 1024)).isTrue();
        assertThat(CertificatePdfValidator.isValid("not a pdf %%EOF".getBytes(), 1024)).isFalse();
        assertThat(CertificatePdfValidator.isValid("%PDF-1.4 but truncated".getBytes(), 1024)).isFalse();
        assertThat(CertificatePdfValidator.isValid(valid, 8)).isFalse();
    }
}
