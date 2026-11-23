package co.ritech.dashboard;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.Sample;
import co.ritech.engine.CalcEngineClient.StatisticsResult;
import co.ritech.simulation.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard (ADMIN)")
@SecurityRequirement(name = "bearer")
public class DashboardController {

    private final SimulationService simulations;
    private final CalcEngineClient calcEngine;
    private final RitechProperties properties;

    public DashboardController(SimulationService simulations, CalcEngineClient calcEngine,
                               RitechProperties properties) {
        this.simulations = simulations;
        this.calcEngine = calcEngine;
        this.properties = properties;
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "KPIs computed by the calc-engine, filterable by user and date range")
    public StatisticsResult statistics(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        var range = DateRange.of(from, to, properties.timezone());
        var samples = simulations.chronological(userId, range).stream()
                .map(s -> new Sample(s.getInitialCapital(), s.getReturnPct()))
                .toList();
        return calcEngine.calculateStatistics(samples);
    }
}
