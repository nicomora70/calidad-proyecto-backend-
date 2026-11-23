package co.ritech.news;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import co.ritech.TestFixtures;
import co.ritech.common.DateRange;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.SentimentResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private final Instant now = Instant.parse("2026-11-21T03:00:00Z"); // 2026-11-20 22:00 in Bogota
    private NewsArticleRepository repository;
    private CalcEngineClient calcEngine;
    private NewsService service;

    @BeforeEach
    void setUp() {
        repository = mock(NewsArticleRepository.class);
        calcEngine = mock(CalcEngineClient.class);
        service = new NewsService(repository, calcEngine, TestFixtures.properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void dailySentimentDefaultsToTodayInInstallationTimeZone() {
        var scores = List.of(new BigDecimal("0.4"));
        // 2026-11-20 in Bogota (UTC-5) = [05:00Z on the 20th, 05:00Z on the 21st)
        when(repository.sentimentScoresBetween(
                        eq(Instant.parse("2026-11-20T05:00:00Z")), eq(Instant.parse("2026-11-21T05:00:00Z"))))
                .thenReturn(scores);
        var expected = new SentimentResult(1, new BigDecimal("0.4"), "Bullish", 1, 0, 0);
        when(calcEngine.calculateSentiment(scores)).thenReturn(expected);

        var daily = service.dailySentiment(null);

        assertThat(daily.date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(daily.sentiment()).isEqualTo(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchDelegatesFiltersToRepository() {
        Pageable page = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), eq(page))).thenReturn(Page.empty());

        assertThat(service.search(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), "bullish", page))
                .isEmpty();
    }

    @Test
    void searchRejectsInvertedRange() {
        assertThatThrownBy(() -> service.search(
                        LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20), null, PageRequest.of(0, 20)))
                .isInstanceOf(DateRange.InvalidDateRangeException.class);
    }

    @Test
    void headlinesIncludeSentimentLabelWhenPresent() {
        var labelled = new NewsArticle(
                new NewsItem("https://a", "Nasdaq rallies", null, null, now, BigDecimal.ONE, "Bullish"), now);
        var plain = new NewsArticle(new NewsItem("https://b", "Fed meets", null, null, now, null, null), now);
        when(repository.findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(any(), any()))
                .thenReturn(List.of(labelled, plain));

        assertThat(service.recentHeadlines()).containsExactly("Nasdaq rallies (Bullish)", "Fed meets");
    }
}
