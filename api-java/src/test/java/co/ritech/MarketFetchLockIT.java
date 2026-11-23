package co.ritech;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.ritech.market.MarketDataProvider;
import co.ritech.market.MarketQuote;
import co.ritech.market.MarketVariation;
import co.ritech.market.MarketVariationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-07: when the market cache is empty or expired, concurrent simulations trigger a single
 * provider call (PostgreSQL advisory lock) and all of them get the same figure.
 * Own container and symbol so no other test's cache is involved.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
            "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET,
            "ritech.market.symbol=LOCKTEST",
            "ritech.news.refresh-on-startup=false"
        })
class MarketFetchLockIT {

    private static final int CALLERS = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private MarketDataProvider provider;

    @Autowired
    private MarketVariationService service;

    @Test
    void concurrentCallersFetchTheProviderOnlyOnce() throws Exception {
        var quote = new MarketQuote("LOCKTEST", LocalDate.of(2026, 11, 20), new BigDecimal("1.2500"), "FIXED");
        when(provider.fetchLatestQuote(anyString())).thenAnswer(inv -> {
            Thread.sleep(300); // slow provider: without the lock, every caller would fetch
            return quote;
        });

        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        var start = new CountDownLatch(1);
        List<Future<MarketVariation>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CALLERS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.currentVariation();
                }));
            }
            start.countDown();
            for (Future<MarketVariation> result : results) {
                MarketVariation variation = result.get(30, TimeUnit.SECONDS);
                assertThat(variation.getChangePct()).isEqualByComparingTo("1.25");
                assertThat(variation.getMarketDate()).isEqualTo(quote.marketDate());
            }
        } finally {
            pool.shutdownNow();
        }

        verify(provider, times(1)).fetchLatestQuote("LOCKTEST");
    }
}
