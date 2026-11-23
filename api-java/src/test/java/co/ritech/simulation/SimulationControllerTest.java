package co.ritech.simulation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SimulationService service;

    @Test
    void createsSimulation() throws Exception {
        var simulation = new Simulation(null, new BigDecimal("1000000.00"), "COP", "QQQ",
                LocalDate.of(2026, 11, 20), new BigDecimal("1.0"), new BigDecimal("2"),
                new BigDecimal("2.0"), new BigDecimal("1020000.00"), new BigDecimal("20000.00"),
                Instant.parse("2026-11-20T15:00:00Z"));
        when(service.simulate(any(), any())).thenReturn(simulation);

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 1000000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.finalAmount").value(1020000.00))
                .andExpect(jsonPath("$.currency").value("COP"));
    }

    @Test
    void missingCapitalIsValidationError() throws Exception {
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void insufficientCapitalMapsToProblemDetail() throws Exception {
        when(service.simulate(any(), any())).thenThrow(new InsufficientCapitalException(
                new BigDecimal("50000"), new BigDecimal("100000"), "COP"));

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialCapital\": 50000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPITAL"));
    }
}
