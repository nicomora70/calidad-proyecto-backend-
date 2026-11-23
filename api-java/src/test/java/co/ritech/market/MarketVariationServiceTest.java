package co.ritech.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.ritech.TestFixtures;

class MarketVariationServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final MarketQuote quote =
            new MarketQuote("QQQ", LocalDate.of(2026, 11, 20), new BigDecimal("0.8"), "TEST");
    private MarketDataProvider provider;
    private MarketVariationRepository repository;
    private MarketVariationService service;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        repository = mock(MarketVariationRepository.class);
        when(repository.save(any(MarketVariation.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new MarketVariationService(provider, repository, TestFixtures.properties(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void reusesFreshCachedValue() {
        var cached = new MarketVariation(quote, now.minusSeconds(3600));
        when(repository.findFirstBySymbolOrderByFetchedAtDesc("QQQ")).thenReturn(Optional.of(cached));

        assertThat(service.currentVariation()).isSameAs(cached);
        verify(provider, never()).fetchLatestQuote(any());
    }

    @Test
    void fetchesWhenCacheIsStale() {
        var stale = new MarketVariation(quote, now.minusSeconds(7 * 3600));
        when(repository.findFirstBySymbolOrderByFetchedAtDesc("QQQ")).thenReturn(Optional.of(stale));
        when(provider.fetchLatestQuote("QQQ")).thenReturn(quote);
        when(repository.findBySymbolAndMarketDate("QQQ", quote.marketDate())).thenReturn(Optional.empty());

        MarketVariation result = service.currentVariation();

        assertThat(result.getFetchedAt()).isEqualTo(now);
        assertThat(result.getChangePct()).isEqualByComparingTo("0.8");
    }
}
