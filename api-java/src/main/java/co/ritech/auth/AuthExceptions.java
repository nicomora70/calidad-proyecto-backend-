package co.ritech.auth;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    /** Deliberately generic: never reveals whether the email exists. */
    public static class InvalidCredentialsException extends BusinessException {
        public InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
    }

    public static class AccountLockedException extends BusinessException {
        private final Instant lockedUntil;

        public AccountLockedException(Instant lockedUntil) {
            super(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again after " + lockedUntil);
            this.lockedUntil = lockedUntil;
        }

        public Instant getLockedUntil() {
            return lockedUntil;
        }
    }

    public static class InvalidRefreshTokenException extends BusinessException {
        public InvalidRefreshTokenException() {
            super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
        }
    }

    public static class InvalidCurrentPasswordException extends BusinessException {
        public InvalidCurrentPasswordException() {
            super(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
    }

    public static class PasswordReusedException extends BusinessException {
        public PasswordReusedException() {
            super(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED", "The new password must differ from the current one");
        }
    }
}
