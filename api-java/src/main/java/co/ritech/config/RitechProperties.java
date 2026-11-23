package co.ritech.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
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
        Cors cors,
        Security security,
        @DefaultValue("America/Bogota") ZoneId timezone) {

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

    public record Security(
            /* HMAC-SHA256 key, at least 32 bytes. Unique per client installation. */
            String jwtSecret,
            @DefaultValue("ritech-api") String jwtIssuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl,
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("") String bootstrapAdminEmail,
            @DefaultValue("") String bootstrapAdminPassword,
            @DefaultValue("Administrator") String bootstrapAdminName) {
    }
}
