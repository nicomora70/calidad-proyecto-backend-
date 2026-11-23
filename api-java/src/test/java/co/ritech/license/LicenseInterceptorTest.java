package co.ritech.license;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import co.ritech.license.LicenseExceptions.LicenseExpiredException;
import co.ritech.license.LicenseExceptions.ModuleNotLicensedException;
import co.ritech.license.LicenseModel.Module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseInterceptorTest {

    private static boolean call(LicenseService license, String method, String path) {
        return new LicenseInterceptor(license)
                .preHandle(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), null);
    }

    private static LicenseService readOnly() {
        LicenseService license = mock(LicenseService.class);
        when(license.writable()).thenReturn(false);
        when(license.hasModule(any())).thenReturn(true);
        return license;
    }

    @Test
    void readOnlyBlocksWritesButNotReadsLoginOrRenewal() {
        var license = readOnly();

        assertThatThrownBy(() -> call(license, "POST", "/api/v1/simulations"))
                .isInstanceOf(LicenseExpiredException.class);
        assertThatThrownBy(() -> call(license, "PUT", "/api/v1/me/notification-preferences"))
                .isInstanceOf(LicenseExpiredException.class);
        assertThat(call(license, "GET", "/api/v1/me/simulations")).isTrue();
        assertThat(call(license, "POST", "/api/v1/auth/login")).isTrue();
        assertThat(call(license, "POST", "/api/v1/auth/change-password")).isTrue();
        assertThat(call(license, "POST", "/api/v1/license")).isTrue();
    }

    @Test
    void moduleOutsideThePlanIsForbidden() {
        LicenseService license = mock(LicenseService.class);
        when(license.writable()).thenReturn(true);
        when(license.hasModule(Module.SIMULATIONS)).thenReturn(true);

        assertThat(call(license, "POST", "/api/v1/simulations")).isTrue();
        assertThatThrownBy(() -> call(license, "GET", "/api/v1/news"))
                .isInstanceOf(ModuleNotLicensedException.class)
                .hasMessageContaining("NEWS");
    }

    @Test
    void endpointToModuleMapping() {
        assertThat(LicenseInterceptor.moduleFor("POST", "/api/v1/simulations")).isEqualTo(Module.SIMULATIONS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/simulations")).isNull(); // history is always readable
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/dashboard/statistics"))
                .isEqualTo(Module.DASHBOARD);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/simulations/1/alert"))
                .isEqualTo(Module.AI_ALERTS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/me/alerts")).isEqualTo(Module.AI_ALERTS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/news")).isEqualTo(Module.NEWS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/news/sentiment"))
                .isEqualTo(Module.NEWS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/me/notifications"))
                .isEqualTo(Module.NOTIFICATIONS);
        assertThat(LicenseInterceptor.moduleFor("PATCH", "/api/v1/me/notifications/1/read"))
                .isEqualTo(Module.NOTIFICATIONS);
        assertThat(LicenseInterceptor.moduleFor("PUT", "/api/v1/me/notification-preferences"))
                .isEqualTo(Module.NOTIFICATIONS);
        assertThat(LicenseInterceptor.moduleFor("POST", "/api/v1/projections")).isEqualTo(Module.PROJECTIONS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/me/simulations/export"))
                .isEqualTo(Module.EXPORTS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/audit/export")).isEqualTo(Module.EXPORTS);
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/audit")).isNull();
        assertThat(LicenseInterceptor.moduleFor("GET", "/api/v1/settings/branding"))
                .isNull();
    }
}
