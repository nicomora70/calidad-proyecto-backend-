package co.ritech.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.auth.AuthDtos.TokenResponse;
import co.ritech.auth.AuthExceptions.AccountLockedException;
import co.ritech.auth.AuthExceptions.InvalidCredentialsException;
import co.ritech.auth.AuthExceptions.InvalidCurrentPasswordException;
import co.ritech.auth.AuthExceptions.InvalidRefreshTokenException;
import co.ritech.auth.AuthExceptions.PasswordReusedException;
import co.ritech.common.BusinessException;
import co.ritech.config.RitechProperties;
import co.ritech.security.CurrentUser;
import co.ritech.security.JwtService;
import co.ritech.user.User;
import co.ritech.user.UserDtos.UserResponse;
import co.ritech.user.UserExceptions.UserNotFoundException;
import co.ritech.user.UserRepository;

/**
 * Login, refresh-token rotation, logout and password change.
 *
 * Failed attempts and lockouts must be persisted even though the request ends
 * in an error, hence {@code noRollbackFor = BusinessException.class}.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final RitechProperties.Security settings;
    private final Clock clock;
    /** Compared against when the email does not exist, so response time does not leak it. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit,
                       RitechProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.settings = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(String email, String password) {
        Instant now = clock.instant();
        User user = users.findByEmailAndDeletedAtIsNull(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(null, "LOGIN_FAILED", "USER", null, "unknown email");
            throw new InvalidCredentialsException();
        }
        if (user.isLocked(now)) {
            audit.record(user.getId(), "LOGIN_BLOCKED", "USER", user.getId(), null);
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = user.registerFailedLogin(settings.maxFailedAttempts(), settings.lockDuration(), now);
            audit.record(user.getId(), locked ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", "USER", user.getId(), null);
            throw new InvalidCredentialsException();
        }
        if (!user.canLogIn()) {
            audit.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), "inactive account");
            throw new InvalidCredentialsException();
        }
        user.registerSuccessfulLogin(now);
        audit.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), null);
        return issueTokens(user, now);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked()) {
            // A revoked token being reused means it was probably stolen: kill every session.
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            audit.record(token.getUserId(), "REFRESH_TOKEN_REUSED", "USER", token.getUserId(), null);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findByIdAndDeletedAtIsNull(token.getUserId())
                .filter(User::canLogIn)
                .orElse(null);
        token.revoke(now);
        if (user == null) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user, now);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke(clock.instant());
            audit.record(token.getUserId(), "LOGOUT", "USER", token.getUserId(), null);
        });
    }

    @Transactional
    public TokenResponse changePassword(CurrentUser current, String currentPassword, String newPassword) {
        User user = users.findByIdAndDeletedAtIsNull(current.id())
                .orElseThrow(() -> new UserNotFoundException(current.id()));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        if (currentPassword.equals(newPassword)) {
            throw new PasswordReusedException();
        }
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), false, now);
        refreshTokens.revokeAllForUser(user.getId(), now); // sign out every other device
        audit.record(user.getId(), "PASSWORD_CHANGED", "USER", user.getId(), null);
        return issueTokens(user, now);
    }

    private TokenResponse issueTokens(User user, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiresAt = now.plus(settings.refreshTokenTtl());
        refreshTokens.save(new RefreshToken(user.getId(), hash(rawRefresh), refreshExpiresAt, now));
        return new TokenResponse(jwtService.issueAccessToken(user), "Bearer",
                jwtService.accessTokenTtlSeconds(), rawRefresh, refreshExpiresAt, UserResponse.from(user));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
