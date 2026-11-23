package co.ritech;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.ReturnResult;
import co.ritech.engine.CalcEngineClient.SentimentResult;
import co.ritech.engine.CalcEngineClient.StatisticsResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow of a fresh client installation against a real PostgreSQL
 * (Flyway migrations + Hibernate schema validation). The calc-engine is mocked;
 * its own contract is covered by the Python tests and the docker smoke test.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
            "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET,
            "ritech.security.bootstrap-admin-email=admin@ritech.co",
            "ritech.security.bootstrap-admin-password=" + InstallationFlowIT.BOOTSTRAP_PASSWORD,
            "ritech.market.provider=fixed",
            "ritech.market.fixed-variation-pct=1.5"
        })
class InstallationFlowIT {

    static final String BOOTSTRAP_PASSWORD = "bootstrap-pass-1";
    static final String ADMIN_PASSWORD = "admin-final-pass-1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CalcEngineClient calcEngine;

    @BeforeEach
    void stubEngine() {
        when(calcEngine.calculateReturn(any(), any())).thenAnswer(inv -> {
            BigDecimal capital = inv.getArgument(0);
            BigDecimal variation = inv.getArgument(1);
            BigDecimal ret = variation.multiply(BigDecimal.TWO);
            BigDecimal finalAmount =
                    capital.multiply(BigDecimal.ONE.add(ret.movePointLeft(2))).setScale(2, RoundingMode.HALF_EVEN);
            return new ReturnResult(
                    capital.setScale(2),
                    variation,
                    BigDecimal.TWO,
                    ret,
                    finalAmount,
                    finalAmount.subtract(capital).setScale(2),
                    false);
        });
        when(calcEngine.calculateSentiment(any()))
                .thenAnswer(inv -> new SentimentResult(
                        ((java.util.List<?>) inv.getArgument(0)).size(), BigDecimal.ZERO, "Neutral", 0, 0, 0));
        when(calcEngine.calculateStatistics(any()))
                .thenAnswer(inv -> new StatisticsResult(
                        ((java.util.List<?>) inv.getArgument(0)).size(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));
    }

    @Test
    void fullInstallationFlow() throws Exception {
        String adminToken = adminToken();

        // Admin creates an investor with a temporary password
        String investorId = read(postJson("/api/users", adminToken, """
                {"fullName":"Ana Inversora","email":"ana@cliente.co","role":"INVESTOR",
                 "temporaryPassword":"temporal-123"}""").andExpect(status().isCreated()), "$.id");

        // Duplicate email (case-insensitive) is rejected
        postJson("/api/users", adminToken, """
                {"fullName":"Otra","email":"ANA@cliente.co","role":"INVESTOR","temporaryPassword":"temporal-123"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        // Investor logs in: must change password before simulating
        String tempToken = read(
                login("ana@cliente.co", "temporal-123")
                        .andExpect(jsonPath("$.user.mustChangePassword").value(true)),
                "$.accessToken");
        postJson("/api/simulations", tempToken, "{\"initialCapital\": 1000000}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        String investorToken = read(
                postJson(
                                "/api/auth/change-password",
                                tempToken,
                                "{\"currentPassword\":\"temporal-123\",\"newPassword\":\"ana-segura-2026\"}")
                        .andExpect(status().isOk()),
                "$.accessToken");

        // Simulation: fixed variation 1.5% -> return 3% -> 1.030.000
        String simulationId = read(
                postJson("/api/simulations", investorToken, "{\"initialCapital\": 1000000}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.userId").value(investorId))
                        .andExpect(jsonPath("$.finalAmount").value(1030000.00)),
                "$.id");

        postJson("/api/simulations", investorToken, "{\"initialCapital\": 50000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPITAL"));

        // AI alert (template provider) is generated asynchronously after the commit
        assertThat(awaitAlertStatus(simulationId, investorToken)).isEqualTo(200);
        mvc.perform(get("/api/simulations/" + simulationId + "/alert")
                        .header("Authorization", "Bearer " + investorToken))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.title").value("El NASDAQ 100 subió 1,50 % y tu simulación ganó 3,00 %"));
        mvc.perform(get("/api/me/alerts").header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // The ready alert lands in the in-app inbox (no SMTP configured: in-app only)
        assertThat(awaitUnread(investorToken)).isEqualTo(1);
        String notificationId = read(
                mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + investorToken))
                        .andExpect(jsonPath("$.content[0].type").value("AI_ALERT_READY")),
                "$.content[0].id");
        mvc.perform(patch("/api/me/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + investorToken))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/me/notification-preferences").header("Authorization", "Bearer " + investorToken))
                .andExpect(jsonPath("$.variationThresholdPct").value(2.00))
                .andExpect(jsonPath("$.emailAvailable").value(false));
        mvc.perform(put("/api/me/notification-preferences")
                        .header("Authorization", "Bearer " + investorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"inAppEnabled\":true,\"variationThresholdPct\":1.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationThresholdPct").value(1.25));

        // History: own list, own detail; investor cannot see the global list or the dashboard
        mvc.perform(get("/api/me/simulations").header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/simulations/" + simulationId).header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/simulations").header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/statistics").header("Authorization", "Bearer " + investorToken))
                .andExpect(status().isForbidden());

        // Admin sees everything
        mvc.perform(get("/api/simulations").param("userId", investorId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/dashboard/statistics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // Audit log recorded the important actions
        mvc.perform(get("/api/audit")
                        .param("action", "SIMULATION_CREATED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value(investorId));

        // Soft delete keeps the simulation; the investor can no longer log in
        mvc.perform(delete("/api/users/" + investorId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        login("ana@cliente.co", "ana-segura-2026").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/simulations/" + simulationId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /** Polls the unread counter until it is positive, up to 5 s. */
    private int awaitUnread(String token) throws Exception {
        int count = 0;
        for (int i = 0; i < 50 && count == 0; i++) {
            count = JsonPath.read(
                    mvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + token))
                            .andReturn()
                            .getResponse()
                            .getContentAsString(),
                    "$.count");
            if (count == 0) {
                Thread.sleep(100);
            }
        }
        return count;
    }

    /** Polls the alert endpoint while it answers 202 (PENDING), up to 5 s. */
    private int awaitAlertStatus(String simulationId, String token) throws Exception {
        int status = 0;
        for (int i = 0; i < 50; i++) {
            status = mvc.perform(get("/api/simulations/" + simulationId + "/alert")
                            .header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (status != 202) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }

    @Test
    void newsAreIngestedOnStartupAndDeduplicated() throws Exception {
        String adminToken = adminToken();

        // The fixed provider ran on startup: 3 sample articles, newest first
        mvc.perform(get("/api/news").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Tech stocks lift the Nasdaq 100 on strong earnings"));
        mvc.perform(get("/api/news").param("sentiment", "neutral").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // A manual refresh finds the same URLs and inserts nothing
        mvc.perform(post("/api/news/refresh").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(3))
                .andExpect(jsonPath("$.inserted").value(0));

        mvc.perform(get("/api/news/sentiment").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentiment.label").exists());
    }

    @Test
    void refreshTokenRotationAndReuseDetection() throws Exception {
        adminToken();
        String refresh1 = read(login("admin@ritech.co", ADMIN_PASSWORD), "$.refreshToken");
        String refresh2 = read(
                postJson("/api/auth/refresh", null, "{\"refreshToken\":\"" + refresh1 + "\"}")
                        .andExpect(status().isOk()),
                "$.refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh1);

        // Reusing the rotated token revokes the whole family, including refresh2
        postJson("/api/auth/refresh", null, "{\"refreshToken\":\"" + refresh1 + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        postJson("/api/auth/refresh", null, "{\"refreshToken\":\"" + refresh2 + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterFiveFailedAttempts() throws Exception {
        String adminToken = adminToken();
        String email = "lock-" + UUID.randomUUID() + "@cliente.co";
        postJson("/api/users", adminToken, """
                {"fullName":"Lock Test","email":"%s","role":"INVESTOR","temporaryPassword":"temporal-123"}""".formatted(email)).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(email, "temporal-123")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void lastAdminCannotBeDemoted() throws Exception {
        String adminToken = adminToken();
        String adminId = read(mvc.perform(get("/api/me").header("Authorization", "Bearer " + adminToken)), "$.id");
        mvc.perform(patch("/api/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INVESTOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void publicAndProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        // A stale/invalid bearer token must not break the public refresh endpoint
        postJson("/api/auth/refresh", "not-a-jwt", "{\"refreshToken\":\"unknown\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    /** Logs in as the bootstrap admin, changing its temporary password the first time. */
    private String adminToken() throws Exception {
        var result = login("admin@ritech.co", ADMIN_PASSWORD).andReturn();
        if (result.getResponse().getStatus() == 200) {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        }
        String temp = read(login("admin@ritech.co", BOOTSTRAP_PASSWORD).andExpect(status().isOk()), "$.accessToken");
        return read(
                postJson(
                                "/api/auth/change-password",
                                temp,
                                "{\"currentPassword\":\"" + BOOTSTRAP_PASSWORD + "\",\"newPassword\":\""
                                        + ADMIN_PASSWORD + "\"}")
                        .andExpect(status().isOk()),
                "$.accessToken");
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
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
