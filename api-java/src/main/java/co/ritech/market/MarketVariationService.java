package co.ritech.market;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.config.RitechProperties;

/**
 * Returns the current variation of the reference symbol, reusing the cached
 * value while it is younger than the configured TTL. This protects the
 * Alpha Vantage free quota (25 calls/day) and makes every simulation of the
 * same window use the same market figure.
 */
@Service
public class MarketVariationService {

    private final MarketDataProvider provider;
    private final MarketVariationRepository repository;
    private final MarketFetchLock lock;
    private final RitechProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MarketVariationService(
            MarketDataProvider provider,
            MarketVariationRepository repository,
            MarketFetchLock lock,
            RitechProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.provider = provider;
        this.repository = repository;
        this.lock = lock;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public MarketVariation currentVariation() {
        String symbol = properties.market().symbol();
        Optional<MarketVariation> fresh = freshCached(symbol);
        if (fresh.isPresent()) {
            return fresh.get();
        }

        lock.acquire(symbol);
        // Another request may have refreshed the cache while we waited for the lock.
        fresh = freshCached(symbol);
        if (fresh.isPresent()) {
            return fresh.get();
        }

        Instant now = clock.instant();
        MarketQuote quote = provider.fetchLatestQuote(symbol);
        MarketVariation variation = repository
                .findBySymbolAndMarketDate(symbol, quote.marketDate())
                .map(existing -> {
                    existing.refresh(quote, now);
                    return existing;
                })
                .orElseGet(() -> repository.save(new MarketVariation(quote, now)));
        events.publishEvent(
                new MarketVariationFetched(variation.getSymbol(), variation.getMarketDate(), variation.getChangePct()));
        return variation;
    }

    private Optional<MarketVariation> freshCached(String symbol) {
        Duration ttl = properties.market().cacheTtl();
        Instant now = clock.instant();
        return repository
                .findFirstBySymbolOrderByFetchedAtDesc(symbol)
                .filter(v -> v.getFetchedAt().plus(ttl).isAfter(now));
    }
}
