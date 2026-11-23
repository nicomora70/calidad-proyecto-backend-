package co.ritech;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import co.ritech.config.RitechProperties;
import co.ritech.license.LicenseService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestFixtures {

    public static final String JWT_SECRET = "test-secret-test-secret-test-secret-0123456789";

    private TestFixtures() {}

    public static RitechProperties properties() {
        return new RitechProperties(
                new RitechProperties.Simulation(new BigDecimal("100000"), "COP"),
                new RitechProperties.Market(
                        "fixed", "QQQ", "", "http://unused", new BigDecimal("1.0"), Duration.ofHours(6)),
                new RitechProperties.CalcEngine("http://unused", Duration.ofSeconds(1)),
                new RitechProperties.Cors(List.of("http://localhost:3000")),
                new RitechProperties.Security(
                        JWT_SECRET,
                        "ritech-api",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        5,
                        Duration.ofMinutes(15),
                        "",
                        "",
                        "Administrator",
                        false),
                new RitechProperties.Ai(
                        "template",
                        "",
                        "http://unused",
                        "gemini-test",
                        Duration.ofSeconds(1),
                        new BigDecimal("5"),
                        new BigDecimal("0.30"),
                        new BigDecimal("2.50")),
                new RitechProperties.News("fixed", "QQQ", "-", 4, false, 5, Duration.ofHours(48)),
                new RitechProperties.Notifications(
                        "RiTech <no-reply@test>", 3, Duration.ofMinutes(1), new BigDecimal("2.00")),
                new RitechProperties.License("disabled", "/nonexistent/license.json", "classpath:unused", 30),
                new RitechProperties.RateLimit(true, 20, 120, 300),
                ZoneId.of("America/Bogota"));
    }

    /** License with every module enabled (LICENSE_MODE=disabled). */
    public static LicenseService allModules() {
        LicenseService license = mock(LicenseService.class);
        when(license.hasModule(any())).thenReturn(true);
        when(license.writable()).thenReturn(true);
        return license;
    }
}
