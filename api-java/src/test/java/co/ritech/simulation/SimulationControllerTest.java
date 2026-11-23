package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.ritech.TestFixtures;
import co.ritech.security.SecurityConfig;
import co.ritech.user.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class SimulationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SimulationService service;

    static JwtRequestPostProcessor as(Role role, boolean mustChangePassword) {
        return jwt().jwt(j -> j.subject(USER_ID.toString())
                        .claim("email", "user@example.com")
                        .claim("role", role.name())
                        .claim("pwd_change", mustChangePassword))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static Simulation sample() {
        return new Simulation(
                USER_ID,
                new BigDecimal("1000000.00"),
                "COP",
                "QQQ",
                LocalDate.of(2026, 11, 20),
                new BigDecimal("1.0"),
                new BigDecimal("2"),
                new BigDecimal("2.0"),
                new BigDecimal("1020000.00"),
                new BigDecimal("20000.00"),
                Instant.parse("2026-11-20T15:00:00Z"));
    }

    @Test
    void investorCreatesSimulationOwnedByTokenSubject() throws Exception {
        when(service.simulate(eq(USER_ID), any())).thenReturn(sample());

        mvc.perform(post("/api/simulations")
                        .with(as(Role.INVESTOR, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.finalAmount").value(1020000.00));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void traceIdFromCallerIsEchoedInHeaderAndErrorBody() throws Exception {
        mvc.perform(post("/api/simulations")
                        .header("X-Request-Id", "frontend-trace-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "frontend-trace-42"))
                .andExpect(jsonPath("$.traceId").value("frontend-trace-42"));
    }

    @Test
    void itManagerCannotSimulate() throws Exception {
        mvc.perform(post("/api/simulations")
                        .with(as(Role.IT_MANAGER, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void investorCannotListEverybodysSimulations() throws Exception {
        mvc.perform(get("/api/simulations").with(as(Role.INVESTOR, false))).andExpect(status().isForbidden());
    }

    @Test
    void temporaryPasswordBlocksBusinessEndpoints() throws Exception {
        mvc.perform(post("/api/simulations")
                        .with(as(Role.INVESTOR, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void missingCapitalIsValidationError() throws Exception {
        mvc.perform(post("/api/simulations")
                        .with(as(Role.INVESTOR, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void insufficientCapitalMapsToProblemDetail() throws Exception {
        when(service.simulate(any(), any()))
                .thenThrow(new InsufficientCapitalException(new BigDecimal("50000"), new BigDecimal("100000"), "COP"));

        mvc.perform(post("/api/simulations")
                        .with(as(Role.INVESTOR, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 50000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPITAL"));
    }

    @Test
    void malformedIdIsBadRequestWithCode() throws Exception {
        mvc.perform(get("/api/simulations/not-a-uuid").with(as(Role.INVESTOR, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
