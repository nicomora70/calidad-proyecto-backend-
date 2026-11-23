package co.ritech.security;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

import co.ritech.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Users created by an admin (and the bootstrap admin) get a temporary password.
 * Until they change it, only the endpoints below are reachable.
 */
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED =
            Set.of("/api/v1/auth/change-password", "/api/v1/auth/logout", "/api/v1/auth/refresh", "/api/v1/me");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token
                && CurrentUser.from(token.getToken()).mustChangePassword()
                && !ALLOWED.contains(request.getRequestURI())) {
            throw new PasswordChangeRequiredException();
        }
        return true;
    }

    public static class PasswordChangeRequiredException extends BusinessException {
        public PasswordChangeRequiredException() {
            super(
                    HttpStatus.FORBIDDEN,
                    "PASSWORD_CHANGE_REQUIRED",
                    "You must change your temporary password before continuing");
        }
    }
}
