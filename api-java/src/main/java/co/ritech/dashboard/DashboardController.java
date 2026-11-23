package co.ritech.dashboard;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.Sample;
import co.ritech.engine.CalcEngineClient.StatisticsResult;
import co.ritech.simulation.SimulationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Global statistics. Will be restricted to ADMIN in Phase 1. */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final SimulationRepository simulations;
    private final CalcEngineClient calcEngine;

    public DashboardController(SimulationRepository simulations, CalcEngineClient calcEngine) {
        this.simulations = simulations;
        this.calcEngine = calcEngine;
    }

    @GetMapping("/statistics")
    @Transactional(readOnly = true)
    @Operation(summary = "Aggregated KPIs computed by the calc-engine")
    public StatisticsResult statistics() {
        var samples = simulations.findAllByOrderByCreatedAtAsc().stream()
                .map(s -> new Sample(s.getInitialCapital(), s.getReturnPct()))
                .toList();
        return calcEngine.calculateStatistics(samples);
    }
}
