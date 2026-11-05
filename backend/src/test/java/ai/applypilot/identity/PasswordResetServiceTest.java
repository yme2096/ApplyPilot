package ai.applypilot.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    @Test void resetTokenIsHashedSingleUseAndRevokesRefreshTokens() throws Exception {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var provider = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        var user = mock(UserEntity.class);
        var userId = UUID.randomUUID();
        when(user.getEmail()).thenReturn("person@example.test");
        when(user.getId()).thenReturn(userId);
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(user));
        when(provider.getIfAvailable()).thenReturn(sender);
        var service = new PasswordResetService(users, tokens, refresh, encoder, provider,
            "http://localhost:5174", "smtp.example.test");

        service.request("person@example.test");

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        String text = message.getValue().getText();
        var match = java.util.regex.Pattern.compile("resetToken=([A-Za-z0-9_-]+)").matcher(text);
        assertThat(match.find()).isTrue();
        String rawToken = match.group(1);
        var saved = org.mockito.ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(tokens).save(saved.capture());
        var token = saved.getValue();
        String tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        assertThat(token.tokenHash).isEqualTo(tokenHash).isNotEqualTo(rawToken);
        assertThat(token.active()).isTrue();

        when(tokens.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(encoder.encode("new-password-123")).thenReturn("bcrypt-hash");
        service.confirm(rawToken, "new-password-123");
        verify(user).changePassword("bcrypt-hash");
        verify(refresh).revokeAllForUser(eq(userId), any(Instant.class));
        assertThat(token.active()).isFalse();
        assertThatThrownBy(() -> service.confirm(rawToken, "new-password-123"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400);
    }

    @Test void expiredTokenIsRejected() {
        var token = new PasswordResetTokenEntity(mock(UserEntity.class), "hash", Instant.now().minusSeconds(1));
        assertThat(token.active()).isFalse();
    }

    @Test void unknownEmailDoesNotSendMailOrPersistToken() {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var provider = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        var service = new PasswordResetService(users, tokens, refresh, encoder, provider,
            "http://localhost:5174", "smtp.example.test");

        service.request("unknown@example.test");

        verifyNoInteractions(sender, tokens);
    }

    @Test void smtpDeliveryFailureDoesNotRevealWhetherEmailExists() {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var provider = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        var user = mock(UserEntity.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(user));
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var service = new PasswordResetService(users, tokens, refresh, encoder, provider,
            "http://localhost:5174", "smtp.example.test");

        service.request("person@example.test");

        verify(tokens, never()).save(any());
    }

    @Test void missingSmtpReturnsControlledServiceUnavailableBeforeEmailLookup() {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var service = new PasswordResetService(users, tokens, refresh, encoder, provider,
            "http://localhost:5174", "");

        assertThatThrownBy(() -> service.request("any@example.test"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(users, tokens);
    }
}
