package co.ritech.license;

import java.util.Set;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import co.ritech.license.LicenseExceptions.LicenseExpiredException;
import co.ritech.license.LicenseExceptions.ModuleNotLicensedException;
import co.ritech.license.LicenseModel.Module;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces the license on every /api request:
 * 1. read-only mode: without a valid license only reads, authentication and the license
 *    renewal itself are allowed (402 LICENSE_EXPIRED);
 * 2. module gating: endpoints of modules outside the plan answer 403 MODULE_NOT_LICENSED.
 */
public class LicenseInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final LicenseService license;

    public LicenseInterceptor(LicenseService license) {
        this.license = license;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!READ_METHODS.contains(method) && !alwaysWritable(path) && !license.writable()) {
            throw new LicenseExpiredException();
        }
        Module module = moduleFor(method, path);
        if (module != null && !license.hasModule(module)) {
            throw new ModuleNotLicensedException(module);
        }
        return true;
    }

    /** Logging in/out and renewing must keep working while the license is expired. */
    private static boolean alwaysWritable(String path) {
        return PATHS.match("/api/auth/**", path) || "/api/license".equals(path);
    }

    static Module moduleFor(String method, String path) {
        if (PATHS.match("/api/**/export", path)) {
            return Module.EXPORTS;
        }
        if ("POST".equals(method) && "/api/simulations".equals(path)) {
            return Module.SIMULATIONS;
        }
        if (PATHS.match("/api/dashboard/**", path)) {
            return Module.DASHBOARD;
        }
        if (PATHS.match("/api/simulations/*/alert", path) || "/api/me/alerts".equals(path)) {
            return Module.AI_ALERTS;
        }
        if (PATHS.match("/api/news/**", path) || "/api/news".equals(path)) {
            return Module.NEWS;
        }
        if (PATHS.match("/api/me/notifications/**", path)
                || "/api/me/notifications".equals(path)
                || "/api/me/notification-preferences".equals(path)) {
            return Module.NOTIFICATIONS;
        }
        if (PATHS.match("/api/projections/**", path) || "/api/projections".equals(path)) {
            return Module.PROJECTIONS;
        }
        return null;
    }
}
