package co.ritech.engine;

import java.math.BigDecimal;
import java.util.List;

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
        return post("/calculations/returns", new ReturnRequest(initialCapital, marketVariationPct), ReturnResult.class);
    }

    public StatisticsResult calculateStatistics(List<Sample> samples) {
        return post("/calculations/statistics", new StatisticsRequest(samples), StatisticsResult.class);
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
}
