package co.ritech.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.ritech.TestFixtures;
import co.ritech.security.SecurityConfig;
import co.ritech.simulation.SimulationNotFoundException;
import co.ritech.simulation.SimulationService;
import co.ritech.user.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiAlertController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class AiAlertControllerTest {

    private static final UUID SIMULATION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AiAlertService alerts;

    @MockitoBean
    private SimulationService simulations;

    private static JwtRequestPostProcessor as(Role role, boolean mustChangePassword) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("email", "user@example.com")
                        .claim("role", role.name())
                        .claim("pwd_change", mustChangePassword))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static AiAlert alert(boolean done) {
        AiAlert alert = new AiAlert(SIMULATION_ID, UUID.randomUUID(), Instant.parse("2026-11-20T15:00:00Z"));
        if (done) {
            alert.markDone(
                    new GeneratedAlert("Título", "Contenido", "template", "template-v1", 0, 0),
                    BigDecimal.ZERO,
                    Instant.parse("2026-11-20T15:00:01Z"));
        }
        return alert;
    }

    @Test
    void doneAlertReturns200WithDisclaimer() throws Exception {
        when(alerts.forSimulation(SIMULATION_ID)).thenReturn(alert(true));

        mvc.perform(get("/api/v1/simulations/" + SIMULATION_ID + "/alert").with(as(Role.INVESTOR, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.title").value("Título"))
                .andExpect(jsonPath("$.disclaimer").value(AiAlertController.DISCLAIMER));
    }

    @Test
    void pendingAlertReturns202() throws Exception {
        when(alerts.forSimulation(SIMULATION_ID)).thenReturn(alert(false));

        mvc.perform(get("/api/v1/simulations/" + SIMULATION_ID + "/alert").with(as(Role.INVESTOR, false)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void alertOfSomeoneElsesSimulationIsNotFound() throws Exception {
        when(simulations.get(eq(SIMULATION_ID), any())).thenThrow(new SimulationNotFoundException(SIMULATION_ID));

        mvc.perform(get("/api/v1/simulations/" + SIMULATION_ID + "/alert").with(as(Role.INVESTOR, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIMULATION_NOT_FOUND"));
        verify(alerts, never()).forSimulation(any());
    }

    @Test
    void simulationWithoutAlertIsNotFound() throws Exception {
        when(alerts.forSimulation(SIMULATION_ID)).thenThrow(new AiAlertNotFoundException(SIMULATION_ID));

        mvc.perform(get("/api/v1/simulations/" + SIMULATION_ID + "/alert").with(as(Role.INVESTOR, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_ALERT_NOT_FOUND"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me/alerts")).andExpect(status().isUnauthorized());
    }
}
