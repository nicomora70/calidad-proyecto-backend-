package co.ritech.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.common.BusinessException;
import co.ritech.config.RitechProperties;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.ProjectionResult;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationRepository;
import co.ritech.simulation.InsufficientCapitalException;

/**
 * Scenario projection (Enterprise): Java validates the business rules and gathers the real
 * stored daily history; the Monte Carlo runs in calc-engine. Nothing is persisted: it is an
 * estimate, recomputed on demand.
 */
@Service
public class ProjectionService {

    static final int HISTORY_DAYS = 250;
    static final int MIN_HISTORY = 5;

    private final MarketVariationRepository market;
    private final CalcEngineClient calcEngine;
    private final AuditService audit;
    private final RitechProperties properties;

    public ProjectionService(
            MarketVariationRepository market,
            CalcEngineClient calcEngine,
            AuditService audit,
            RitechProperties properties) {
        this.market = market;
        this.calcEngine = calcEngine;
        this.audit = audit;
        this.properties = properties;
    }

    public static class InsufficientMarketHistoryException extends BusinessException {
        public InsufficientMarketHistoryException(int available) {
            super(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INSUFFICIENT_MARKET_HISTORY",
                    "At least " + MIN_HISTORY + " market days are needed to project; " + available + " stored");
        }
    }

    public record Projection(
            ProjectionResult result, String currency, String symbol, LocalDate historyFrom, LocalDate historyTo) {}

    @Transactional
    public Projection project(UUID userId, BigDecimal initialCapital, int horizonDays) {
        var rules = properties.simulation();
        if (initialCapital.compareTo(rules.minCapital()) < 0) {
            throw new InsufficientCapitalException(initialCapital, rules.minCapital(), rules.currency());
        }
        String symbol = properties.market().symbol();
        List<MarketVariation> history = market.findTop250BySymbolOrderByMarketDateDesc(symbol);
        if (history.size() < MIN_HISTORY) {
            throw new InsufficientMarketHistoryException(history.size());
        }
        var result = calcEngine.calculateProjection(
                initialCapital,
                history.stream().map(MarketVariation::getChangePct).toList(),
                horizonDays);
        audit.record(
                userId,
                "PROJECTION_CREATED",
                "PROJECTION",
                null,
                "capital=" + initialCapital.toPlainString() + " horizon_days=" + horizonDays + " sample="
                        + history.size());
        var dates = history.stream().map(MarketVariation::getMarketDate).toList();
        return new Projection(
                result,
                rules.currency(),
                symbol,
                dates.stream().min(Comparator.naturalOrder()).orElseThrow(),
                dates.stream().max(Comparator.naturalOrder()).orElseThrow());
    }
}
