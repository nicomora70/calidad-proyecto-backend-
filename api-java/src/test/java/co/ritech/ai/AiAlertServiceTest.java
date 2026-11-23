package co.ritech.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;

import co.ritech.TestFixtures;
import co.ritech.news.NewsService;
import co.ritech.simulation.Simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAlertServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private AiAlertRepository repository;
    private ApplicationEventPublisher events;
    private NewsService news;
    private FakeGenerator generator;
    private AiAlertService service;

    /** Fake provider: no network in tests. */
    static final class FakeGenerator implements AiAlertGenerator {
        RuntimeException failure;
        boolean billable = true;
        int calls;

        @Override
        public GeneratedAlert generate(AlertContext context) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return new GeneratedAlert("Título", "Contenido", "fake-model", "alert-v1", 1_000_000, 200_000);
        }

        @Override
        public boolean billable() {
            return billable;
        }
    }

    @BeforeEach
    void setUp() {
        repository = mock(AiAlertRepository.class);
        events = mock(ApplicationEventPublisher.class);
        news = mock(NewsService.class);
        when(news.recentHeadlines()).thenReturn(List.of("Tech rally (Bullish)"));
        generator = new FakeGenerator();
        when(repository.save(any(AiAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.spentSince(any())).thenReturn(BigDecimal.ZERO);
        service = new AiAlertService(
                repository,
                generator,
                events,
                news,
                TestFixtures.allModules(),
                TestFixtures.properties(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Simulation simulation() {
        return new Simulation(
                UUID.randomUUID(),
                new BigDecimal("1000000.00"),
                "COP",
                "QQQ",
                LocalDate.of(2026, 11, 20),
                new BigDecimal("1.5"),
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("1030000.00"),
                new BigDecimal("30000.00"),
                Instant.parse("2026-11-20T15:00:00Z"));
    }

    private AiAlert pendingAlert() {
        AiAlert alert = new AiAlert(UUID.randomUUID(), UUID.randomUUID(), now);
        when(repository.findById(alert.getId())).thenReturn(Optional.of(alert));
        return alert;
    }

    private void generate(AiAlert alert) {
        service.generate(new AiAlertRequested(alert.getId(), AlertContext.from(simulation(), List.of())));
    }

    @Test
    void requestStoresPendingAlertAndPublishesEvent() {
        Simulation simulation = simulation();

        service.requestFor(simulation);

        verify(repository).save(any(AiAlert.class));
        verify(events)
                .publishEvent(ArgumentMatchers.<AiAlertRequested>argThat(
                        e -> e.context().headlines().equals(List.of("Tech rally (Bullish)"))));
    }

    @Test
    void successfulGenerationStoresTextTokensAndCost() {
        AiAlert alert = pendingAlert();

        generate(alert);

        assertThat(alert.getStatus()).isEqualTo(AiAlertStatus.DONE);
        assertThat(alert.getTitle()).isEqualTo("Título");
        assertThat(alert.getPromptTokens()).isEqualTo(1_000_000);
        // 1M input tokens x 0.30 + 0.2M output tokens x 2.50 = 0.80 USD
        assertThat(alert.getCostUsd()).isEqualByComparingTo("0.80");
        assertThat(alert.getCompletedAt()).isEqualTo(now);
        verify(events).publishEvent(new AiAlertReady(alert.getUserId(), alert.getSimulationId(), "Título"));
    }

    @Test
    void providerFailureMarksAlertFailedWithoutThrowing() {
        AiAlert alert = pendingAlert();
        generator.failure = new AiAlertException("Could not reach Gemini");

        generate(alert);

        assertThat(alert.getStatus()).isEqualTo(AiAlertStatus.FAILED);
        assertThat(alert.getFailureReason()).isEqualTo("Could not reach Gemini");
        verify(events, never()).publishEvent(any(AiAlertReady.class));
    }

    @Test
    void budgetReachedSkipsBillableProvider() {
        AiAlert alert = pendingAlert();
        when(repository.spentSince(any())).thenReturn(new BigDecimal("5.00")); // budget = 5

        generate(alert);

        assertThat(alert.getStatus()).isEqualTo(AiAlertStatus.FAILED);
        assertThat(alert.getFailureReason()).isEqualTo(AiAlertService.BUDGET_EXCEEDED);
        assertThat(generator.calls).isZero();
    }

    @Test
    void budgetJustBelowLimitStillGenerates() {
        AiAlert alert = pendingAlert();
        when(repository.spentSince(any())).thenReturn(new BigDecimal("4.999999"));

        generate(alert);

        assertThat(alert.getStatus()).isEqualTo(AiAlertStatus.DONE);
    }

    @Test
    void freeProviderIgnoresBudget() {
        AiAlert alert = pendingAlert();
        generator.billable = false;

        generate(alert);

        assertThat(alert.getStatus()).isEqualTo(AiAlertStatus.DONE);
        verify(repository, never()).spentSince(any());
    }

    @Test
    void alreadyCompletedAlertIsNotRegenerated() {
        AiAlert alert = pendingAlert();
        generate(alert);

        generate(alert);

        assertThat(generator.calls).isEqualTo(1);
    }
}
