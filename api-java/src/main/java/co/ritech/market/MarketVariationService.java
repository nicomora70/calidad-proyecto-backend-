package co.ritech.market;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
    private final RitechProperties properties;
    private final Clock clock;

    public MarketVariationService(MarketDataProvider provider, MarketVariationRepository repository,
                                  RitechProperties properties, Clock clock) {
        this.provider = provider;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MarketVariation currentVariation() {
        String symbol = properties.market().symbol();
        Instant now = clock.instant();
        Duration ttl = properties.market().cacheTtl();

        var cached = repository.findFirstBySymbolOrderByFetchedAtDesc(symbol);
        if (cached.isPresent() && cached.get().getFetchedAt().plus(ttl).isAfter(now)) {
            return cached.get();
        }

        MarketQuote quote = provider.fetchLatestQuote(symbol);
        return repository.findBySymbolAndMarketDate(symbol, quote.marketDate())
                .map(existing -> {
                    existing.refresh(quote, now);
                    return existing;
                })
                .orElseGet(() -> repository.save(new MarketVariation(quote, now)));
    }
}
