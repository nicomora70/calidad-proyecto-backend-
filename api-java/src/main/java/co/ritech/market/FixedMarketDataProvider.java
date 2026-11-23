package co.ritech.market;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import co.ritech.config.RitechProperties;

/**
 * Deterministic provider for local development, demos and tests (no API key needed).
 * Never use it in a client production installation.
 */
@Component
@ConditionalOnProperty(name = "ritech.market.provider", havingValue = "fixed", matchIfMissing = true)
public class FixedMarketDataProvider implements MarketDataProvider {

    private final RitechProperties properties;
    private final Clock clock;

    public FixedMarketDataProvider(RitechProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public MarketQuote fetchLatestQuote(String symbol) {
        return new MarketQuote(symbol, LocalDate.now(clock),
                properties.market().fixedVariationPct(), "FIXED");
    }
}
