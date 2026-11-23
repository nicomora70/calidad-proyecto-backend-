package co.ritech.auth;

import java.time.Instant;

import co.ritech.user.UserDtos;
import co.ritech.user.UserDtos.UserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = UserDtos.PASSWORD_MIN, max = UserDtos.PASSWORD_MAX) String newPassword) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserResponse user) {
    }
}
