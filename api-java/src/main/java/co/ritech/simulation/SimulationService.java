package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.ai.AiAlertService;
import co.ritech.audit.AuditService;
import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.engine.CalcEngineClient;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationService;
import co.ritech.security.CurrentUser;

/**
 * Simulation use case:
 * 1. validate minimum capital (business rule, Java),
 * 2. obtain the current NASDAQ 100 variation (cached),
 * 3. delegate the math to the Python calc-engine,
 * 4. persist the immutable result and audit it,
 * 5. request the AI alert (generated after the commit, best-effort).
 */
@Service
public class SimulationService {

    private final RitechProperties properties;
    private final MarketVariationService marketVariationService;
    private final CalcEngineClient calcEngine;
    private final SimulationRepository repository;
    private final AuditService auditService;
    private final AiAlertService aiAlerts;
    private final Clock clock;

    public SimulationService(
            RitechProperties properties,
            MarketVariationService marketVariationService,
            CalcEngineClient calcEngine,
            SimulationRepository repository,
            AuditService auditService,
            AiAlertService aiAlerts,
            Clock clock) {
        this.properties = properties;
        this.marketVariationService = marketVariationService;
        this.calcEngine = calcEngine;
        this.repository = repository;
        this.auditService = auditService;
        this.aiAlerts = aiAlerts;
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
                userId,
                result.initialCapital(),
                currency,
                variation.getSymbol(),
                variation.getMarketDate(),
                variation.getChangePct(),
                result.leverage(),
                result.returnPct(),
                result.finalAmount(),
                result.profit(),
                clock.instant()));

        auditService.record(
                userId,
                "SIMULATION_CREATED",
                "SIMULATION",
                simulation.getId(),
                "capital=" + initialCapital.toPlainString() + " return_pct=" + result.returnPct());
        aiAlerts.requestFor(simulation);
        return simulation;
    }

    /** Owner or ADMIN; otherwise 404 so ids of other users' simulations are not disclosed. */
    @Transactional(readOnly = true)
    public Simulation get(UUID id, CurrentUser caller) {
        return repository
                .findById(id)
                .filter(s -> caller.isAdmin() || s.getUserId().equals(caller.id()))
                .orElseThrow(() -> new SimulationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Simulation> search(UUID userId, DateRange range, Pageable pageable) {
        return repository.findAll(spec(userId, range), pageable);
    }

    /** Chronological samples for the statistics engine (drawdown depends on order). */
    @Transactional(readOnly = true)
    public List<Simulation> chronological(UUID userId, DateRange range) {
        return repository.findAll(spec(userId, range), Sort.by("createdAt").ascending());
    }

    private static Specification<Simulation> spec(UUID userId, DateRange range) {
        return Specification.allOf(
                SimulationRepository.ownedBy(userId),
                SimulationRepository.createdFrom(range.from()),
                SimulationRepository.createdBefore(range.to()));
    }
}
