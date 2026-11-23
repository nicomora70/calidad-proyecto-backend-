package co.ritech.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditService;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.ProjectionResult;
import co.ritech.market.MarketQuote;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationRepository;
import co.ritech.projection.ProjectionService.InsufficientMarketHistoryException;
import co.ritech.simulation.InsufficientCapitalException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectionServiceTest {

    private static final BigDecimal CAPITAL = new BigDecimal("1000000");
    private MarketVariationRepository market;
    private CalcEngineClient calcEngine;
    private ProjectionService service;

    @BeforeEach
    void setUp() {
        market = mock(MarketVariationRepository.class);
        calcEngine = mock(CalcEngineClient.class);
        service = new ProjectionService(market, calcEngine, mock(AuditService.class), TestFixtures.properties());
    }

    private static List<MarketVariation> days(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new MarketVariation(
                        new MarketQuote("QQQ", LocalDate.of(2026, 11, 20).minusDays(i), new BigDecimal(i), "TEST"),
                        Instant.parse("2026-11-20T15:00:00Z")))
                .toList();
    }

    @Test
    void capitalBelowMinimumIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> service.project(null, new BigDecimal("99999.99"), 20))
                .isInstanceOf(InsufficientCapitalException.class);
        verifyNoInteractions(market, calcEngine);
    }

    @Test
    void fourDaysOfHistoryAreNotEnough() {
        when(market.findTop250BySymbolOrderByMarketDateDesc("QQQ")).thenReturn(days(4));

        assertThatThrownBy(() -> service.project(null, CAPITAL, 20))
                .isInstanceOf(InsufficientMarketHistoryException.class);
        verifyNoInteractions(calcEngine);
    }

    @Test
    void fiveDaysProjectWithTheStoredVariations() {
        when(market.findTop250BySymbolOrderByMarketDateDesc("QQQ")).thenReturn(days(5));
        var low = new CalcEngineClient.Scenario(
                new BigDecimal("950000.00"), new BigDecimal("-50000.00"), new BigDecimal("-5"));
        var mid =
                new CalcEngineClient.Scenario(new BigDecimal("1010000.00"), new BigDecimal("10000.00"), BigDecimal.ONE);
        var high = new CalcEngineClient.Scenario(
                new BigDecimal("1080000.00"), new BigDecimal("80000.00"), new BigDecimal("8"));
        when(calcEngine.calculateProjection(eq(CAPITAL), any(), anyInt()))
                .thenReturn(new ProjectionResult(
                        20,
                        10000,
                        5,
                        BigDecimal.TWO,
                        low,
                        mid,
                        high,
                        new BigDecimal("1012000.00"),
                        new BigDecimal("41.5")));

        var projection = service.project(null, CAPITAL, 20);

        assertThat(projection.result().pessimistic().finalAmount()).isEqualByComparingTo("950000.00");
        assertThat(projection.result().base().returnPct()).isEqualByComparingTo("1");
        assertThat(projection.result().optimistic().profit()).isEqualByComparingTo("80000.00");
        assertThat(projection.result().probabilityOfLossPct()).isEqualByComparingTo("41.5");
        assertThat(projection.result().sampleSize()).isEqualTo(5);
        assertThat(projection.currency()).isEqualTo("COP");
        assertThat(projection.historyFrom()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(projection.historyTo()).isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
