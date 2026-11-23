package co.ritech.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public final class UserExceptions {

    private UserExceptions() {
    }

    public static class UserNotFoundException extends BusinessException {
        public UserNotFoundException(UUID id) {
            super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + id + " does not exist");
        }
    }

    public static class EmailAlreadyExistsException extends BusinessException {
        public EmailAlreadyExistsException(String email) {
            super(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "A user with email " + email + " already exists");
        }
    }

    public static class LastAdminException extends BusinessException {
        public LastAdminException() {
            super(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "The installation must keep at least one active administrator");
        }
    }
}
