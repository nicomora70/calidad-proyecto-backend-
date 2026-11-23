package co.ritech.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.ritech.config.RitechProperties;
import co.ritech.news.NewsService;
import co.ritech.simulation.Simulation;

/**
 * AI alerts are best-effort: the PENDING row is written with the simulation, and the
 * text is generated in a background thread after the commit. A provider failure marks
 * the alert FAILED; it never rolls back or delays the simulation.
 */
@Service
public class AiAlertService {

    static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    private static final Logger log = LoggerFactory.getLogger(AiAlertService.class);
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final AiAlertRepository repository;
    private final AiAlertGenerator generator;
    private final ApplicationEventPublisher events;
    private final NewsService news;
    private final RitechProperties properties;
    private final Clock clock;

    public AiAlertService(
            AiAlertRepository repository,
            AiAlertGenerator generator,
            ApplicationEventPublisher events,
            NewsService news,
            RitechProperties properties,
            Clock clock) {
        this.repository = repository;
        this.generator = generator;
        this.events = events;
        this.news = news;
        this.properties = properties;
        this.clock = clock;
    }

    /** Must run inside the simulation transaction. Headlines come from the stored news (Phase 3). */
    public void requestFor(Simulation simulation) {
        AiAlert alert = repository.save(new AiAlert(simulation.getId(), simulation.getUserId(), clock.instant()));
        events.publishEvent(new AiAlertRequested(alert.getId(), AlertContext.from(simulation, news.recentHeadlines())));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(AiAlertRequested event) {
        generate(event);
    }

    void generate(AiAlertRequested event) {
        AiAlert alert = repository.findById(event.alertId()).orElse(null);
        if (alert == null || alert.getStatus() != AiAlertStatus.PENDING) {
            return;
        }
        try {
            if (generator.billable() && budgetExceeded()) {
                log.warn("AI monthly budget reached; alert {} not generated", alert.getId());
                alert.markFailed(BUDGET_EXCEEDED, clock.instant());
            } else {
                GeneratedAlert generated = generator.generate(event.context());
                alert.markDone(generated, cost(generated), clock.instant());
            }
        } catch (RuntimeException ex) {
            log.warn("AI alert {} failed: {}", alert.getId(), ex.getMessage());
            alert.markFailed(ex.getMessage(), clock.instant());
        }
        repository.save(alert);
    }

    @Transactional(readOnly = true)
    public AiAlert forSimulation(UUID simulationId) {
        return repository
                .findBySimulationId(simulationId)
                .orElseThrow(() -> new AiAlertNotFoundException(simulationId));
    }

    @Transactional(readOnly = true)
    public Page<AiAlert> forUser(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable);
    }

    private boolean budgetExceeded() {
        Instant monthStart = ZonedDateTime.now(clock.withZone(properties.timezone()))
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
        return repository.spentSince(monthStart).compareTo(properties.ai().monthlyBudgetUsd()) >= 0;
    }

    private BigDecimal cost(GeneratedAlert g) {
        var ai = properties.ai();
        return ai.inputPricePerMtok()
                .multiply(BigDecimal.valueOf(g.promptTokens()))
                .add(ai.outputPricePerMtok().multiply(BigDecimal.valueOf(g.completionTokens())))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
