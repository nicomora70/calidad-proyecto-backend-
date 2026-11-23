package co.ritech.market;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import co.ritech.common.ResilientCall;
import co.ritech.config.RitechProperties;

/**
 * Real daily variation from Alpha Vantage GLOBAL_QUOTE. QQQ is used as a proxy
 * of the NASDAQ 100 because the index itself requires a paid plan.
 * Alpha Vantage answers HTTP 200 even on errors/rate limits, so the body is validated
 * (those cases are not retried: retrying would only burn the daily quota).
 */
@Component
@ConditionalOnProperty(name = "ritech.market.provider", havingValue = "alphavantage")
public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final int TIMEOUT_MS = 10_000;

    private final RestClient restClient;
    private final String apiKey;
    private final ResilientCall resilience = ResilientCall.withDefaults("alpha-vantage");

    public AlphaVantageMarketDataProvider(RitechProperties properties, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restClient = builder.baseUrl(properties.market().alphaVantageUrl())
                .requestFactory(factory)
                .build();
        this.apiKey = properties.market().alphaVantageApiKey();
    }

    @Override
    public MarketQuote fetchLatestQuote(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataUnavailableException("Alpha Vantage API key is not configured");
        }
        JsonNode body;
        try {
            body = resilience.execute(
                    () -> restClient
                            .get()
                            .uri(uri -> uri.queryParam("function", "GLOBAL_QUOTE")
                                    .queryParam("symbol", symbol)
                                    .queryParam("apikey", apiKey)
                                    .build())
                            .retrieve()
                            .body(JsonNode.class),
                    ex -> new MarketDataUnavailableException("Could not reach Alpha Vantage", ex));
        } catch (RestClientException ex) {
            throw new MarketDataUnavailableException("Alpha Vantage rejected the request", ex);
        }
        return parse(symbol, body);
    }

    static MarketQuote parse(String symbol, JsonNode body) {
        JsonNode quote = body == null ? null : body.get("Global Quote");
        if (quote == null || quote.isEmpty()) {
            throw new MarketDataUnavailableException("Alpha Vantage returned no quote (rate limit or invalid key)");
        }
        try {
            String pct = quote.path("10. change percent").asText().trim().replace("%", "");
            LocalDate day = LocalDate.parse(quote.path("07. latest trading day").asText());
            return new MarketQuote(symbol, day, new BigDecimal(pct), "ALPHA_VANTAGE");
        } catch (NumberFormatException | DateTimeException ex) {
            throw new MarketDataUnavailableException("Unexpected Alpha Vantage response format", ex);
        }
    }
}
