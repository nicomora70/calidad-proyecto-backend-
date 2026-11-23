package co.ritech.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import co.ritech.common.RequestIdFilter;
import co.ritech.common.ResilientCall;
import co.ritech.config.RitechProperties;

/**
 * HTTP client of the internal Python calc-engine. All financial math lives there;
 * Java only orchestrates, validates business rules and persists.
 */
@Component
public class CalcEngineClient {

    private static final Logger log = LoggerFactory.getLogger(CalcEngineClient.class);

    private final RestClient restClient;
    private final ResilientCall resilience;

    @Autowired
    public CalcEngineClient(RitechProperties properties, RestClient.Builder builder) {
        this(properties, builder, ResilientCall.withDefaults("calc-engine"));
    }

    CalcEngineClient(RitechProperties properties, RestClient.Builder builder, ResilientCall resilience) {
        var factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) properties.calcEngine().timeout().toMillis();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(properties.calcEngine().baseUrl())
                .requestFactory(factory)
                .requestInterceptor(RequestIdFilter.propagatingInterceptor())
                .build();
        this.resilience = resilience;
    }

    public ReturnResult calculateReturn(BigDecimal initialCapital, BigDecimal marketVariationPct) {
        try {
            return post("/calculations/returns", new ReturnRequest(initialCapital, marketVariationPct), ReturnResult.class);
        } catch (CalcEngineUnavailableException ex) {
            log.warn("Calc-engine unavailable, using local fallback for returns: {}", ex.getMessage());
            return localReturn(initialCapital, marketVariationPct);
        }
    }

    public StatisticsResult calculateStatistics(List<Sample> samples) {
        try {
            return post("/calculations/statistics", new StatisticsRequest(samples), StatisticsResult.class);
        } catch (CalcEngineUnavailableException ex) {
            log.warn("Calc-engine unavailable, using local fallback for statistics: {}", ex.getMessage());
            return localStatistics(samples);
        }
    }

    private static final BigDecimal LEVERAGE = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Fallback: same x2 leverage rule as the calc-engine, loss capped at the capital. */
    static ReturnResult localReturn(BigDecimal capital, BigDecimal variationPct) {
        BigDecimal returnPct = variationPct.multiply(LEVERAGE).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal raw = capital.add(capital.multiply(returnPct).divide(HUNDRED, 10, RoundingMode.HALF_EVEN));
        boolean capped = raw.signum() < 0;
        BigDecimal finalAmount = (capped ? BigDecimal.ZERO : raw).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal profit = finalAmount.subtract(capital).setScale(2, RoundingMode.HALF_EVEN);
        return new ReturnResult(capital, variationPct, LEVERAGE, returnPct, finalAmount, profit, capped);
    }

    static StatisticsResult localStatistics(List<Sample> samples) {
        BigDecimal zero4 = BigDecimal.ZERO.setScale(4);
        if (samples == null || samples.isEmpty()) {
            return new StatisticsResult(0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    zero4, zero4, zero4, zero4, zero4, zero4, zero4);
        }
        int n = samples.size();
        BigDecimal totalCapital = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal best = null;
        BigDecimal worst = null;
        int wins = 0;
        double equity = 1;
        double peak = 1;
        double maxDrawdown = 0;
        for (Sample s : samples) {
            BigDecimal r = s.returnPct();
            totalCapital = totalCapital.add(s.initialCapital());
            totalProfit = totalProfit.add(s.initialCapital().multiply(r).divide(HUNDRED, 10, RoundingMode.HALF_EVEN));
            sum = sum.add(r);
            best = best == null || r.compareTo(best) > 0 ? r : best;
            worst = worst == null || r.compareTo(worst) < 0 ? r : worst;
            if (r.signum() > 0) {
                wins++;
            }
            equity *= Math.max(0, 1 + r.doubleValue() / 100);
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, peak == 0 ? 0 : (peak - equity) / peak * 100);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_EVEN);
        double variance = 0;
        for (Sample s : samples) {
            double d = s.returnPct().doubleValue() - mean.doubleValue();
            variance += d * d;
        }
        BigDecimal stdev = BigDecimal.valueOf(n > 1 ? Math.sqrt(variance / (n - 1)) : 0);
        BigDecimal weighted = totalCapital.signum() == 0 ? BigDecimal.ZERO
                : totalProfit.multiply(HUNDRED).divide(totalCapital, 10, RoundingMode.HALF_EVEN);
        BigDecimal winRate = BigDecimal.valueOf(wins).multiply(HUNDRED).divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_EVEN);
        return new StatisticsResult(n,
                totalCapital.setScale(2, RoundingMode.HALF_EVEN),
                totalProfit.setScale(2, RoundingMode.HALF_EVEN),
                mean.setScale(4, RoundingMode.HALF_EVEN),
                weighted.setScale(4, RoundingMode.HALF_EVEN),
                stdev.setScale(4, RoundingMode.HALF_EVEN),
                best.setScale(4, RoundingMode.HALF_EVEN),
                worst.setScale(4, RoundingMode.HALF_EVEN),
                BigDecimal.valueOf(maxDrawdown).setScale(4, RoundingMode.HALF_EVEN),
                winRate.setScale(4, RoundingMode.HALF_EVEN));
    }

    public ProjectionResult calculateProjection(
            BigDecimal initialCapital, List<BigDecimal> dailyVariationsPct, int horizonDays) {
        return post(
                "/calculations/projection",
                new ProjectionRequest(initialCapital, dailyVariationsPct, horizonDays),
                ProjectionResult.class);
    }

    public SentimentResult calculateSentiment(List<BigDecimal> scores) {
        return post("/calculations/sentiment", new SentimentRequest(scores), SentimentResult.class);
    }

    /** Transient failures are retried with backoff; repeated failures open the circuit. */
    private <T> T post(String path, Object body, Class<T> type) {
        T result;
        try {
            result = resilience.execute(
                    () -> restClient.post().uri(path).body(body).retrieve().body(type),
                    ex -> new CalcEngineUnavailableException("Calc-engine is not available", ex));
        } catch (RestClientException ex) {
            // 4xx or unreadable body: a contract mismatch, not retried
            throw new CalcEngineUnavailableException("Calc-engine rejected the request", ex);
        }
        if (result == null) {
            throw new CalcEngineUnavailableException("Empty response from calc-engine", null);
        }
        return result;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReturnRequest(BigDecimal initialCapital, BigDecimal marketVariationPct) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReturnResult(
            BigDecimal initialCapital,
            BigDecimal marketVariationPct,
            BigDecimal leverage,
            BigDecimal returnPct,
            BigDecimal finalAmount,
            BigDecimal profit,
            boolean cappedLoss) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Sample(BigDecimal initialCapital, BigDecimal returnPct) {}

    public record StatisticsRequest(List<Sample> samples) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StatisticsResult(
            int count,
            BigDecimal totalCapital,
            BigDecimal totalProfit,
            BigDecimal meanReturnPct,
            BigDecimal weightedReturnPct,
            BigDecimal stdevReturnPct,
            BigDecimal bestReturnPct,
            BigDecimal worstReturnPct,
            BigDecimal maxDrawdownPct,
            BigDecimal winRatePct) {}

    public record SentimentRequest(List<BigDecimal> scores) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SentimentResult(
            int count, BigDecimal meanScore, String label, int bullishCount, int neutralCount, int bearishCount) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProjectionRequest(BigDecimal initialCapital, List<BigDecimal> dailyVariationsPct, int horizonDays) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Scenario(BigDecimal finalAmount, BigDecimal profit, BigDecimal returnPct) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProjectionResult(
            int horizonDays,
            int simulations,
            int sampleSize,
            BigDecimal leverage,
            Scenario pessimistic,
            Scenario base,
            Scenario optimistic,
            BigDecimal expectedFinalAmount,
            BigDecimal probabilityOfLossPct) {}
}
