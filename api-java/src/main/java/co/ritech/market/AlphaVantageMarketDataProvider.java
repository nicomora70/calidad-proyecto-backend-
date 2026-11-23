package co.ritech.market;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import co.ritech.config.RitechProperties;

/**
 * Real daily variation from Alpha Vantage GLOBAL_QUOTE. QQQ is used as a proxy
 * of the NASDAQ 100 because the index itself requires a paid plan.
 * Alpha Vantage answers HTTP 200 even on errors/rate limits, so the body is validated.
 */
@Component
@ConditionalOnProperty(name = "ritech.market.provider", havingValue = "alphavantage")
public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private final RestClient restClient;
    private final String apiKey;

    public AlphaVantageMarketDataProvider(RitechProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.market().alphaVantageUrl()).build();
        this.apiKey = properties.market().alphaVantageApiKey();
    }

    @Override
    public MarketQuote fetchLatestQuote(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataUnavailableException("Alpha Vantage API key is not configured");
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode quote = body == null ? null : body.get("Global Quote");
            if (quote == null || quote.isEmpty()) {
                throw new MarketDataUnavailableException(
                        "Alpha Vantage returned no quote (rate limit or invalid key)");
            }
            String pct = quote.path("10. change percent").asText().trim().replace("%", "");
            LocalDate day = LocalDate.parse(quote.path("07. latest trading day").asText());
            return new MarketQuote(symbol, day, new BigDecimal(pct), "ALPHA_VANTAGE");
        } catch (RestClientException | IllegalArgumentException | java.time.DateTimeException ex) {
            throw new MarketDataUnavailableException("Could not obtain the NASDAQ 100 variation", ex);
        }
    }
}
