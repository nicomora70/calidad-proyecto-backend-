package co.ritech.engine;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import co.ritech.config.RitechProperties;

/**
 * HTTP client of the internal Python calc-engine. All financial math lives there;
 * Java only orchestrates, validates business rules and persists.
 */
@Component
public class CalcEngineClient {

    private final RestClient restClient;

    public CalcEngineClient(RitechProperties properties, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) properties.calcEngine().timeout().toMillis();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = builder
                .baseUrl(properties.calcEngine().baseUrl())
                .requestFactory(factory)
                .build();
    }

    public ReturnResult calculateReturn(BigDecimal initialCapital, BigDecimal marketVariationPct) {
        return post("/calculations/returns",
                new ReturnRequest(initialCapital, marketVariationPct), ReturnResult.class);
    }

    public StatisticsResult calculateStatistics(List<Sample> samples) {
        return post("/calculations/statistics", new StatisticsRequest(samples), StatisticsResult.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            T result = restClient.post().uri(path).body(body).retrieve().body(type);
            if (result == null) {
                throw new CalcEngineUnavailableException("Empty response from calc-engine", null);
            }
            return result;
        } catch (RestClientException ex) {
            throw new CalcEngineUnavailableException("Calc-engine is not available", ex);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReturnRequest(BigDecimal initialCapital, BigDecimal marketVariationPct) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReturnResult(BigDecimal initialCapital, BigDecimal marketVariationPct,
                               BigDecimal leverage, BigDecimal returnPct, BigDecimal finalAmount,
                               BigDecimal profit, boolean cappedLoss) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Sample(BigDecimal initialCapital, BigDecimal returnPct) {
    }

    public record StatisticsRequest(List<Sample> samples) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StatisticsResult(int count, BigDecimal totalCapital, BigDecimal totalProfit,
                                   BigDecimal meanReturnPct, BigDecimal weightedReturnPct,
                                   BigDecimal stdevReturnPct, BigDecimal bestReturnPct,
                                   BigDecimal worstReturnPct, BigDecimal maxDrawdownPct,
                                   BigDecimal winRatePct) {
    }
}
