package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.ritech.TestFixtures;
import co.ritech.ai.AiAlertService;
import co.ritech.audit.AuditService;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.ReturnResult;
import co.ritech.market.MarketQuote;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SimulationServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private MarketVariationService marketService;
    private CalcEngineClient calcEngine;
    private SimulationRepository repository;
    private AuditService audit;
    private AiAlertService aiAlerts;
    private SimulationService service;

    @BeforeEach
    void setUp() {
        marketService = mock(MarketVariationService.class);
        calcEngine = mock(CalcEngineClient.class);
        repository = mock(SimulationRepository.class);
        audit = mock(AuditService.class);
        aiAlerts = mock(AiAlertService.class);
        when(repository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SimulationService(
                TestFixtures.properties(),
                marketService,
                calcEngine,
                repository,
                audit,
                aiAlerts,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void rejectsCapitalBelowMinimum() {
        assertThatThrownBy(() -> service.simulate(null, new BigDecimal("99999.99")))
                .isInstanceOf(InsufficientCapitalException.class);
        verifyNoInteractions(marketService, calcEngine, repository, aiAlerts);
    }

    private Simulation ownedBy(UUID owner) {
        return new Simulation(
                owner,
                new BigDecimal("100000.00"),
                "COP",
                "QQQ",
                LocalDate.of(2026, 11, 20),
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("2"),
                new BigDecimal("102000.00"),
                new BigDecimal("2000.00"),
                now);
    }

    @Test
    void ownerAndAdminCanReadSimulationOthersGetNotFound() {
        UUID owner = UUID.randomUUID();
        Simulation simulation = ownedBy(owner);
        when(repository.findById(simulation.getId())).thenReturn(java.util.Optional.of(simulation));

        var ownerUser = new co.ritech.security.CurrentUser(owner, "o@x.co", co.ritech.user.Role.INVESTOR, false);
        var admin = new co.ritech.security.CurrentUser(UUID.randomUUID(), "a@x.co", co.ritech.user.Role.ADMIN, false);
        var stranger =
                new co.ritech.security.CurrentUser(UUID.randomUUID(), "s@x.co", co.ritech.user.Role.INVESTOR, false);

        assertThat(service.get(simulation.getId(), ownerUser)).isSameAs(simulation);
        assertThat(service.get(simulation.getId(), admin)).isSameAs(simulation);
        assertThatThrownBy(() -> service.get(simulation.getId(), stranger))
                .isInstanceOf(SimulationNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAndChronologicalUseSpecifications() {
        UUID owner = UUID.randomUUID();
        var range = new co.ritech.common.DateRange(null, null);
        when(repository.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(ownedBy(owner))));
        when(repository.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(ownedBy(owner)));

        assertThat(service.search(owner, range, org.springframework.data.domain.PageRequest.of(0, 5)))
                .hasSize(1);
        assertThat(service.chronological(null, range)).hasSize(1);
    }

    @Test
    void persistsResultComputedByEngine() {
        var quote = new MarketQuote("QQQ", LocalDate.of(2026, 11, 20), new BigDecimal("1.5000"), "FIXED");
        when(marketService.currentVariation()).thenReturn(new MarketVariation(quote, now));
        var capital = new BigDecimal("1000000.00");
        when(calcEngine.calculateReturn(capital, new BigDecimal("1.5000")))
                .thenReturn(new ReturnResult(
                        capital,
                        new BigDecimal("1.5000"),
                        new BigDecimal("2"),
                        new BigDecimal("3.0000"),
                        new BigDecimal("1030000.00"),
                        new BigDecimal("30000.00"),
                        false));
        UUID userId = UUID.randomUUID();

        Simulation result = service.simulate(userId, capital);

        assertThat(result.getReturnPct()).isEqualByComparingTo("3");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("1030000");
        assertThat(result.getCurrency()).isEqualTo("COP");
        assertThat(result.getSymbol()).isEqualTo("QQQ");
        assertThat(result.getCreatedAt()).isEqualTo(now);
        verify(audit).record(eq(userId), eq("SIMULATION_CREATED"), eq("SIMULATION"), eq(result.getId()), any());
        verify(aiAlerts).requestFor(result);
    }
}
