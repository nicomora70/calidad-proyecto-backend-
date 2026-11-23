package co.ritech.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    /** Password rules: 8..72 chars (72 = BCrypt input limit). */
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotNull Role role,
            /* Temporary password: the user must change it on first login. */
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String temporaryPassword) {
    }

    public record UpdateUserRequest(
            @Size(min = 1, max = 120) String fullName,
            Boolean active) {
    }

    public record ChangeRoleRequest(@NotNull Role role) {
    }

    public record UserResponse(
            UUID id,
            String fullName,
            String email,
            Role role,
            boolean active,
            boolean mustChangePassword,
            Instant lastLoginAt,
            Instant createdAt) {

        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.isActive(), u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }
}
