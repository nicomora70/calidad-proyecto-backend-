package co.ritech.export;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditLog;
import co.ritech.audit.AuditService;
import co.ritech.simulation.Simulation;
import co.ritech.simulation.SimulationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    private final Instant now = Instant.parse("2026-11-23T15:00:00Z");
    private SimulationService simulations;
    private AuditService audit;
    private ExportService service;

    @BeforeEach
    void setUp() {
        simulations = mock(SimulationService.class);
        audit = mock(AuditService.class);
        service = new ExportService(simulations, audit, TestFixtures.properties());
    }

    @Test
    void simulationsCsvHasBomHeaderRowsAndIsAudited() {
        UUID owner = UUID.randomUUID();
        var s = new Simulation(
                owner,
                new BigDecimal("1000000.00"),
                "COP",
                "QQQ",
                LocalDate.of(2026, 11, 20),
                new BigDecimal("1.5000"),
                new BigDecimal("2"),
                new BigDecimal("3.0000"),
                new BigDecimal("1030000.00"),
                new BigDecimal("30000.00"),
                now);
        when(simulations.search(eq(owner), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s)));

        String csv = new String(service.simulations(owner, owner, null, null), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿id,user_id,created_at,initial_capital");
        assertThat(csv).contains(",1000000.00,COP,QQQ,2026-11-20,1.5000,2,3.0000,1030000.00,30000.00\r\n");
        verify(audit).record(eq(owner), eq("EXPORT_CREATED"), eq("EXPORT"), any(), eq("type=simulations rows=1"));
    }

    @Test
    void exportIsCappedAtMaxRows() {
        when(simulations.search(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.simulations(null, null, null, null);

        verify(simulations).search(any(), any(), argThat((Pageable p) -> p.getPageSize() == ExportService.MAX_ROWS));
    }

    @Test
    void textCellsAreQuotedAndFormulaInjectionIsNeutralized() {
        String csv = new String(
                new Csv(List.of("a", "b", "c"))
                        .row(Arrays.asList(Csv.text("=HYPERLINK(\"x\")"), Csv.text("hola, \"mundo\""), null))
                        .bytes(),
                StandardCharsets.UTF_8);

        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\",\"hola, \"\"mundo\"\"\",\r\n");
    }

    @Test
    void negativeNumbersAreNotAltered() {
        String csv = new String(new Csv(List.of("n")).row(List.of("-1.5")).bytes(), StandardCharsets.UTF_8);
        assertThat(csv).endsWith("-1.5\r\n");
    }

    @Test
    void auditCsvExportsEntries() {
        var entry = mock(AuditLog.class);
        when(entry.getAction()).thenReturn("LOGIN_FAILED");
        when(entry.getUserAgent()).thenReturn("@evil");
        when(audit.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        String csv = new String(service.audit(null, null, null, null, null), StandardCharsets.UTF_8);

        assertThat(csv).contains("LOGIN_FAILED").contains("'@evil");
    }
}
