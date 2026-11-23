package co.ritech.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditService;
import co.ritech.auth.AuthExceptions.AccountLockedException;
import co.ritech.auth.AuthExceptions.InvalidCredentialsException;
import co.ritech.auth.AuthExceptions.InvalidCurrentPasswordException;
import co.ritech.auth.AuthExceptions.InvalidRefreshTokenException;
import co.ritech.security.CurrentUser;
import co.ritech.security.JwtService;
import co.ritech.user.Role;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private RefreshTokenRepository tokens;
    private AuditService audit;
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(RefreshTokenRepository.class);
        audit = mock(AuditService.class);
        JwtService jwt = mock(JwtService.class);
        when(jwt.issueAccessToken(any())).thenReturn("access-token");
        when(tokens.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AuthService(
                users, tokens, encoder, jwt, audit, TestFixtures.properties(), Clock.fixed(now, ZoneOffset.UTC));

        user = new User("Ana", "Ana@Example.com", Role.INVESTOR, encoder.encode("correct-password"), false, now);
        when(users.findByEmailAndDeletedAtIsNull("ana@example.com")).thenReturn(Optional.of(user));
        when(users.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void loginSucceedsWithNormalizedEmail() {
        var response = service.login("  ANA@example.com ", "correct-password");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
        verify(audit).record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), null);
    }

    @Test
    void refreshTokenIsStoredHashed() {
        var response = service.login("ana@example.com", "correct-password");

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(AuthService.hash(response.refreshToken()))
                .isNotEqualTo(response.refreshToken());
    }

    @Test
    void unknownEmailIsGenericError() {
        when(users.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nobody@example.com", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void fifthFailedAttemptLocksAccount() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("ana@example.com", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.isLocked(now)).isTrue();
        assertThatThrownBy(() -> service.login("ana@example.com", "correct-password"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void inactiveUserCannotLogIn() {
        user.setActive(false, now);
        assertThatThrownBy(() -> service.login("ana@example.com", "correct-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesToken() {
        var stored = new RefreshToken(user.getId(), AuthService.hash("raw"), now.plusSeconds(3600), now);
        when(tokens.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(stored));

        var response = service.refresh("raw");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotEqualTo("raw");
    }

    @Test
    void reusingRevokedTokenRevokesAllSessions() {
        var stored = new RefreshToken(user.getId(), AuthService.hash("raw"), now.plusSeconds(3600), now);
        stored.revoke(now);
        when(tokens.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokens).revokeAllForUser(eq(user.getId()), any());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        var stored = new RefreshToken(user.getId(), AuthService.hash("raw"), now.minusSeconds(1), now);
        when(tokens.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        var current = new CurrentUser(user.getId(), user.getEmail(), Role.INVESTOR, true);
        assertThatThrownBy(() -> service.changePassword(current, "wrong", "new-password-123"))
                .isInstanceOf(InvalidCurrentPasswordException.class);
    }

    @Test
    void changePasswordClearsTemporaryFlagAndRevokesSessions() {
        var temp = new User("Bob", "bob@example.com", Role.INVESTOR, encoder.encode("temporary-1"), true, now);
        when(users.findByIdAndDeletedAtIsNull(temp.getId())).thenReturn(Optional.of(temp));

        service.changePassword(
                new CurrentUser(temp.getId(), temp.getEmail(), Role.INVESTOR, true), "temporary-1", "brand-new-pass");

        assertThat(temp.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("brand-new-pass", temp.getPasswordHash())).isTrue();
        verify(tokens).revokeAllForUser(eq(temp.getId()), any());
    }
}
