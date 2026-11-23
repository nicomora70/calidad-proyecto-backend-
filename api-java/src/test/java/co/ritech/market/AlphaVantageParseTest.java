package co.ritech.market;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantageParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesGlobalQuote() throws Exception {
        var body = mapper.readTree("""
                {"Global Quote": {"01. symbol": "QQQ", "07. latest trading day": "2026-11-20",
                                  "10. change percent": "-1.2345%"}}""");

        MarketQuote quote = AlphaVantageMarketDataProvider.parse("QQQ", body);

        assertThat(quote.changePct()).isEqualByComparingTo("-1.2345");
        assertThat(quote.marketDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void rateLimitResponseIsUnavailable() throws Exception {
        var body = mapper.readTree("{\"Information\": \"rate limit reached\"}");
        assertThatThrownBy(() -> AlphaVantageMarketDataProvider.parse("QQQ", body))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void malformedNumberIsUnavailable() throws Exception {
        var body = mapper.readTree("""
                {"Global Quote": {"07. latest trading day": "2026-11-20", "10. change percent": "n/a"}}""");
        assertThatThrownBy(() -> AlphaVantageMarketDataProvider.parse("QQQ", body))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
