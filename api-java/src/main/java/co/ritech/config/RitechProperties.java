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
        Ai ai,
        News news,
        Notifications notifications,
        License license,
        @DefaultValue("America/Bogota") ZoneId timezone) {

    public record Simulation(
            @DefaultValue("100000") BigDecimal minCapital,
            @DefaultValue("COP") String currency) {}

    public record Market(
            @DefaultValue("fixed") String provider,
            @DefaultValue("QQQ") String symbol,
            @DefaultValue("") String alphaVantageApiKey,

            @DefaultValue("https://www.alphavantage.co/query")
            String alphaVantageUrl,

            @DefaultValue("1.0") BigDecimal fixedVariationPct,
            @DefaultValue("6h") Duration cacheTtl) {}

    public record CalcEngine(
            @DefaultValue("http://localhost:8000") String baseUrl,
            @DefaultValue("5s") Duration timeout) {}

    public record Cors(
            @DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

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
            @DefaultValue("Administrator") String bootstrapAdminName) {}

    public record Ai(
            /* template (no external calls) | gemini */
            @DefaultValue("template") String provider,
            @DefaultValue("") String geminiApiKey,

            @DefaultValue("https://generativelanguage.googleapis.com")
            String geminiUrl,

            @DefaultValue("gemini-2.5-flash") String geminiModel,
            @DefaultValue("20s") Duration timeout,
            /* Spending cap per calendar month (installation time zone); billable providers only */
            @DefaultValue("5") BigDecimal monthlyBudgetUsd,
            @DefaultValue("0") BigDecimal inputPricePerMtok,
            @DefaultValue("0") BigDecimal outputPricePerMtok) {}

    public record News(
            /* fixed (sample feed) | alphavantage (shares the market API key) */
            @DefaultValue("fixed") String provider,
            @DefaultValue("QQQ") String tickers,
            @DefaultValue("0 0 */6 * * *") String refreshCron,
            /* Alpha Vantage free plan: 25 calls/day shared with market data */
            @DefaultValue("4") int maxDailyCalls,
            @DefaultValue("true") boolean refreshOnStartup,
            /* Headlines given to the AI prompt and how far back they may go */
            @DefaultValue("5") int alertHeadlines,
            @DefaultValue("48h") Duration alertWindow) {}

    public record Notifications(
            @DefaultValue("RiTech <no-reply@ritech.local>") String mailFrom,
            /* E-mail delivery attempts; the wait doubles after each failure */
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("1m") Duration retryBackoff,
            /* Default |daily variation| (%) that triggers MARKET_VARIATION */
            @DefaultValue("2.00") BigDecimal defaultVariationThresholdPct) {}

    public record License(
            /* disabled (development, demo, CI) | enforced (client installations) */
            @DefaultValue("disabled") String mode,
            /* Installed on first boot if the database has no license yet */
            @DefaultValue("/app/license/license.json") String file,
            /* Vendor public key; the private key never leaves the vendor (tools/license) */
            @DefaultValue("classpath:license/public-key.pem")
            String publicKeyLocation,

            @DefaultValue("30") int expiryWarningDays) {}
}
