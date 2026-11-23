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
        var result = mock(ProjectionResult.class);
        when(calcEngine.calculateProjection(eq(CAPITAL), any(), anyInt())).thenReturn(result);

        var projection = service.project(null, CAPITAL, 20);

        assertThat(projection.result()).isSameAs(result);
        assertThat(projection.currency()).isEqualTo("COP");
        assertThat(projection.historyFrom()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(projection.historyTo()).isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
