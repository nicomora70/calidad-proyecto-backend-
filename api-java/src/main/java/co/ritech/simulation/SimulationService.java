package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.config.RitechProperties;
import co.ritech.engine.CalcEngineClient;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationService;

/**
 * Simulation use case:
 * 1. validate minimum capital (business rule, Java),
 * 2. obtain the current NASDAQ 100 variation (cached),
 * 3. delegate the math to the Python calc-engine,
 * 4. persist the immutable result and audit it.
 */
@Service
public class SimulationService {

    private final RitechProperties properties;
    private final MarketVariationService marketVariationService;
    private final CalcEngineClient calcEngine;
    private final SimulationRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public SimulationService(RitechProperties properties, MarketVariationService marketVariationService,
                             CalcEngineClient calcEngine, SimulationRepository repository,
                             AuditService auditService, Clock clock) {
        this.properties = properties;
        this.marketVariationService = marketVariationService;
        this.calcEngine = calcEngine;
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Simulation simulate(UUID userId, BigDecimal initialCapital) {
        BigDecimal minimum = properties.simulation().minCapital();
        String currency = properties.simulation().currency();
        if (initialCapital.compareTo(minimum) < 0) {
            throw new InsufficientCapitalException(initialCapital, minimum, currency);
        }

        MarketVariation variation = marketVariationService.currentVariation();
        var result = calcEngine.calculateReturn(initialCapital, variation.getChangePct());

        Simulation simulation = repository.save(new Simulation(
                userId, result.initialCapital(), currency, variation.getSymbol(),
                variation.getMarketDate(), variation.getChangePct(), result.leverage(),
                result.returnPct(), result.finalAmount(), result.profit(), clock.instant()));

        auditService.record(userId, "SIMULATION_CREATED", "SIMULATION", simulation.getId(),
                "capital=" + initialCapital.toPlainString() + " return_pct=" + result.returnPct());
        return simulation;
    }

    @Transactional(readOnly = true)
    public Page<Simulation> list(UUID userId, Pageable pageable) {
        return userId == null ? repository.findAll(pageable) : repository.findByUserId(userId, pageable);
    }
}
