package co.ritech.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import co.ritech.user.Role;

/** The authenticated caller, resolved from the access token (see CurrentUserArgumentResolver). */
public record CurrentUser(UUID id, String email, Role role, boolean mustChangePassword) {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_PASSWORD_CHANGE = "pwd_change";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_EMAIL),
                Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_PASSWORD_CHANGE)));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
