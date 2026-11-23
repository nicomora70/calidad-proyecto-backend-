package co.ritech.news;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.news.NewsExceptions.NewsUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantageNewsParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesFeedAndSkipsMalformedArticles() throws Exception {
        var body = mapper.readTree("""
                {"items": "3", "feed": [
                  {"title": "Nasdaq rallies", "url": "https://news/1", "time_published": "20261120T093000",
                   "summary": "S", "source": "Reuters", "overall_sentiment_score": 0.2512,
                   "overall_sentiment_label": "Somewhat-Bullish"},
                  {"title": "No url"},
                  {"title": "Bad date", "url": "https://news/3", "time_published": "yesterday"}
                ]}""");

        var items = AlphaVantageNewsProvider.parse(body);

        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertThat(item.url()).isEqualTo("https://news/1");
        assertThat(item.sentimentScore()).isEqualByComparingTo("0.2512");
        assertThat(item.sentimentLabel()).isEqualTo("Somewhat-Bullish");
        // 09:30 US/Eastern (EST in November) = 14:30 UTC
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-11-20T14:30:00Z"));
    }

    @Test
    void missingScoreIsStoredAsNull() throws Exception {
        var body = mapper.readTree("""
                {"feed": [{"title": "T", "url": "https://news/1", "time_published": "20261120T093000"}]}""");
        assertThat(AlphaVantageNewsProvider.parse(body).get(0).sentimentScore()).isNull();
    }

    @Test
    void rateLimitResponseIsUnavailable() throws Exception {
        var body = mapper.readTree("{\"Information\": \"rate limit reached\"}");
        assertThatThrownBy(() -> AlphaVantageNewsProvider.parse(body)).isInstanceOf(NewsUnavailableException.class);
    }
}
