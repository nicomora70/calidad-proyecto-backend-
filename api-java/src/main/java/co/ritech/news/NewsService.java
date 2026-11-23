package co.ritech.news;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.engine.CalcEngineClient;
import co.ritech.engine.CalcEngineClient.SentimentResult;

/** Read side of the news: feed, daily sentiment (computed by calc-engine) and headlines for the AI. */
@Service
public class NewsService {

    private final NewsArticleRepository repository;
    private final CalcEngineClient calcEngine;
    private final RitechProperties properties;
    private final Clock clock;

    public NewsService(
            NewsArticleRepository repository, CalcEngineClient calcEngine, RitechProperties properties, Clock clock) {
        this.repository = repository;
        this.calcEngine = calcEngine;
        this.properties = properties;
        this.clock = clock;
    }

    public record DailySentiment(LocalDate date, Sentiment sentiment) {}

    /** API view (camelCase) of the calc-engine result. */
    public record Sentiment(
            int count, BigDecimal meanScore, String label, int bullishCount, int neutralCount, int bearishCount) {

        static Sentiment from(SentimentResult r) {
            return new Sentiment(
                    r.count(), r.meanScore(), r.label(), r.bullishCount(), r.neutralCount(), r.bearishCount());
        }
    }

    @Transactional(readOnly = true)
    public Page<NewsArticle> search(LocalDate from, LocalDate to, String sentimentLabel, Pageable pageable) {
        var range = DateRange.of(from, to, properties.timezone());
        return repository.findAll(
                Specification.allOf(
                        NewsArticleRepository.publishedFrom(range.from()),
                        NewsArticleRepository.publishedBefore(range.to()),
                        NewsArticleRepository.withSentiment(sentimentLabel)),
                pageable);
    }

    /** Sentiment indicator of one calendar day (installation time zone); today by default. */
    @Transactional(readOnly = true)
    public DailySentiment dailySentiment(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(properties.timezone()));
        var range = DateRange.of(day, day, properties.timezone());
        var scores = repository.sentimentScoresBetween(range.from(), range.to());
        return new DailySentiment(day, Sentiment.from(calcEngine.calculateSentiment(scores)));
    }

    /** Latest headlines inside the configured window, as "title (label)", for the AI prompt. */
    @Transactional(readOnly = true)
    public List<String> recentHeadlines() {
        var news = properties.news();
        var from = clock.instant().minus(news.alertWindow());
        return repository
                .findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(from, PageRequest.of(0, news.alertHeadlines()))
                .stream()
                .map(a -> a.getSentimentLabel() == null
                        ? a.getTitle()
                        : a.getTitle() + " (" + a.getSentimentLabel() + ")")
                .toList();
    }
}
