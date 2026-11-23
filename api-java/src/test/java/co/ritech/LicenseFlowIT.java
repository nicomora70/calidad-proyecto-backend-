package co.ritech;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.ReturnResult;
import co.ritech.license.TestLicenses;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commercial installation with LICENSE_MODE=enforced: read-only without a license,
 * signature checks, user limit and module gating per plan. Uses a throw-away key pair.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LicenseFlowIT {

    static final String BOOTSTRAP_PASSWORD = "bootstrap-pass-1";
    static final String ADMIN_PASSWORD = "admin-final-pass-1";
    static final TestLicenses LICENSES = new TestLicenses();
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ritech.security.jwt-secret", () -> TestFixtures.JWT_SECRET);
        registry.add("ritech.security.bootstrap-admin-email", () -> "admin@cliente.co");
        registry.add("ritech.security.bootstrap-admin-password", () -> BOOTSTRAP_PASSWORD);
        registry.add("ritech.market.provider", () -> "fixed");
        registry.add("ritech.license.mode", () -> "enforced");
        registry.add("ritech.rate-limit.auth-per-minute", () -> "1000");
        registry.add("ritech.license.file", () -> "/nonexistent/license.json");
        registry.add(
                "ritech.license.public-key-location",
                () -> LICENSES.writePublicKey().toUri().toString());
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CalcEngineClient calcEngine;

    @Test
    void enforcedLicenseLifecycle() throws Exception {
        when(calcEngine.calculateReturn(any(), any()))
                .thenReturn(new ReturnResult(
                        new BigDecimal("1000000.00"),
                        BigDecimal.ONE,
                        BigDecimal.TWO,
                        BigDecimal.TWO,
                        new BigDecimal("1020000.00"),
                        new BigDecimal("20000.00"),
                        false));
        String admin = adminToken();

        // No license: read-only
        mvc.perform(get("/api/v1/license").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MISSING"));
        postJson("/api/v1/simulations", admin, "{\"initialCapital\": 1000000}")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("LICENSE_EXPIRED"));
        mvc.perform(get("/api/v1/me/simulations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // Invalid licenses are rejected
        var expired = LICENSES.issue("BASIC", 2, TODAY.minusDays(30), TODAY.minusDays(1));
        postJson("/api/v1/license", admin, TestLicenses.json(expired))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LICENSE_INVALID"));
        var foreign = new TestLicenses().issue("ENTERPRISE", 99, TODAY, TODAY.plusDays(365));
        postJson("/api/v1/license", admin, TestLicenses.json(foreign))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LICENSE_INVALID"));

        // BASIC license for 2 users
        postJson("/api/v1/license", admin, TestLicenses.json(LICENSES.issue("BASIC", 2, TODAY, TODAY.plusDays(365))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.plan").value("BASIC"))
                .andExpect(jsonPath("$.activeUsers").value(1));

        postJson("/api/v1/users", admin, """
                {"fullName":"Ana","email":"ana@cliente.co","role":"INVESTOR","temporaryPassword":"temporal-123"}""").andExpect(status().isCreated());
        postJson("/api/v1/users", admin, """
                {"fullName":"Luis","email":"luis@cliente.co","role":"INVESTOR","temporaryPassword":"temporal-123"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LICENSE_USER_LIMIT"));

        // Simulations are in BASIC; AI alerts, news and exports are not
        String simulationId = read(
                postJson("/api/v1/simulations", admin, "{\"initialCapital\": 1000000}")
                        .andExpect(status().isCreated()),
                "$.id");
        mvc.perform(get("/api/v1/simulations/" + simulationId + "/alert").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_LICENSED"));
        mvc.perform(get("/api/v1/news").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/simulations/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());

        // Branding falls back to the licensed company (public endpoint)
        mvc.perform(get("/api/v1/settings/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Cliente S.A.S."));

        // Upgrade to ENTERPRISE: exports become available
        postJson(
                        "/api/v1/license",
                        admin,
                        TestLicenses.json(LICENSES.issue("ENTERPRISE", 10, TODAY, TODAY.plusDays(365))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ENTERPRISE"));
        mvc.perform(get("/api/v1/me/simulations/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    private String adminToken() throws Exception {
        String temp = read(
                postJson(
                                "/api/v1/auth/login",
                                null,
                                "{\"email\":\"admin@cliente.co\",\"password\":\"" + BOOTSTRAP_PASSWORD + "\"}")
                        .andExpect(status().isOk()),
                "$.accessToken");
        // change-password is under /api/v1/auth: allowed even in read-only mode
        return read(
                postJson(
                                "/api/v1/auth/change-password",
                                temp,
                                "{\"currentPassword\":\"" + BOOTSTRAP_PASSWORD + "\",\"newPassword\":\""
                                        + ADMIN_PASSWORD + "\"}")
                        .andExpect(status().isOk()),
                "$.accessToken");
    }

    private ResultActions postJson(String url, String token, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private static String read(ResultActions actions, String path) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path);
    }
}
