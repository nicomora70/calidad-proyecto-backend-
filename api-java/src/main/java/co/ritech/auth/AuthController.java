package co.ritech.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.auth.AuthDtos.ChangePasswordRequest;
import co.ritech.auth.AuthDtos.LoginRequest;
import co.ritech.auth.AuthDtos.RefreshRequest;
import co.ritech.auth.AuthDtos.TokenResponse;
import co.ritech.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in: returns a 15-minute access token and a 7-day refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair (the old one is revoked)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request.refreshToken());
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Change own password; revokes other sessions and returns new tokens")
    public TokenResponse changePassword(
            @Parameter(hidden = true) CurrentUser user, @Valid @RequestBody ChangePasswordRequest request) {
        return service.changePassword(user, request.currentPassword(), request.newPassword());
    }
}
