package co.ritech.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Installation-level configuration. RiTech business constants (symbol,
 * minimum capital, currency) are fixed by default but kept here so each
 * client installation can be audited from a single place.
 */
@ConfigurationProperties(prefix = "ritech")
public record RitechProperties(
        Simulation simulation,
        Market market,
        CalcEngine calcEngine,
        Cors cors) {

    public record Simulation(
            @DefaultValue("100000") BigDecimal minCapital,
            @DefaultValue("COP") String currency) {
    }

    public record Market(
            @DefaultValue("fixed") String provider,
            @DefaultValue("QQQ") String symbol,
            @DefaultValue("") String alphaVantageApiKey,
            @DefaultValue("https://www.alphavantage.co/query") String alphaVantageUrl,
            @DefaultValue("1.0") BigDecimal fixedVariationPct,
            @DefaultValue("6h") Duration cacheTtl) {
    }

    public record CalcEngine(
            @DefaultValue("http://localhost:8000") String baseUrl,
            @DefaultValue("5s") Duration timeout) {
    }

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
    }
}
