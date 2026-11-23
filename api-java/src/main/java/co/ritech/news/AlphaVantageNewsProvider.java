package co.ritech.news;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import co.ritech.common.ResilientCall;
import co.ritech.config.RitechProperties;
import co.ritech.news.NewsExceptions.NewsUnavailableException;

/**
 * Alpha Vantage NEWS_SENTIMENT. Shares the API key (and the free daily quota) with the
 * market provider. Like GLOBAL_QUOTE, errors and rate limits come back as HTTP 200,
 * so the body is validated; malformed articles are skipped instead of failing the batch.
 */
@Component
@ConditionalOnProperty(name = "ritech.news.provider", havingValue = "alphavantage")
public class AlphaVantageNewsProvider implements NewsProvider {

    private static final int TIMEOUT_MS = 10_000;
    private static final int LIMIT = 50;
    private static final DateTimeFormatter TIME_PUBLISHED = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    /* Assumption: Alpha Vantage reports times in US/Eastern, like its market data. */
    private static final ZoneId PUBLISHED_ZONE = ZoneId.of("America/New_York");

    private final RestClient restClient;
    private final String apiKey;
    private final ResilientCall resilience = ResilientCall.withDefaults("alpha-vantage-news");

    public AlphaVantageNewsProvider(RitechProperties properties, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restClient = builder.baseUrl(properties.market().alphaVantageUrl())
                .requestFactory(factory)
                .build();
        this.apiKey = properties.market().alphaVantageApiKey();
    }

    @Override
    public List<NewsItem> fetchLatest(String tickers) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new NewsUnavailableException("Alpha Vantage API key is not configured");
        }
        JsonNode body;
        try {
            body = resilience.execute(
                    () -> restClient
                            .get()
                            .uri(uri -> uri.queryParam("function", "NEWS_SENTIMENT")
                                    .queryParam("tickers", tickers)
                                    .queryParam("sort", "LATEST")
                                    .queryParam("limit", LIMIT)
                                    .queryParam("apikey", apiKey)
                                    .build())
                            .retrieve()
                            .body(JsonNode.class),
                    ex -> new NewsUnavailableException("Could not reach Alpha Vantage", ex));
        } catch (RestClientException ex) {
            throw new NewsUnavailableException("Alpha Vantage rejected the request", ex);
        }
        return parse(body);
    }

    static List<NewsItem> parse(JsonNode body) {
        JsonNode feed = body == null ? null : body.get("feed");
        if (feed == null || !feed.isArray()) {
            throw new NewsUnavailableException("Alpha Vantage returned no feed (rate limit or invalid key)");
        }
        List<NewsItem> items = new ArrayList<>();
        for (JsonNode a : feed) {
            String url = a.path("url").asText("").strip();
            String title = a.path("title").asText("").strip();
            if (url.isEmpty() || title.isEmpty()) {
                continue;
            }
            try {
                Instant published = LocalDateTime.parse(a.path("time_published").asText(), TIME_PUBLISHED)
                        .atZone(PUBLISHED_ZONE)
                        .toInstant();
                JsonNode score = a.get("overall_sentiment_score");
                items.add(new NewsItem(
                        url,
                        title,
                        a.path("summary").asText(null),
                        a.path("source").asText(null),
                        published,
                        score == null || !score.isNumber() ? null : score.decimalValue(),
                        a.path("overall_sentiment_label").asText(null)));
            } catch (DateTimeException ex) {
                // skip the malformed article, keep the rest of the batch
            }
        }
        return items;
    }
}
