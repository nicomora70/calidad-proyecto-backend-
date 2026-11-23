package co.ritech.news;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stored article. Immutable: a URL is ingested once. */
@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    private UUID id;

    @Column(nullable = false, length = 1000, updatable = false)
    private String url;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 100)
    private String source;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "sentiment_score", precision = 6, scale = 4)
    private BigDecimal sentimentScore;

    @Column(name = "sentiment_label", length = 30)
    private String sentimentLabel;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected NewsArticle() {}

    public NewsArticle(NewsItem item, Instant fetchedAt) {
        this.id = UUID.randomUUID();
        this.url = item.url();
        this.title = truncate(item.title(), 500);
        this.summary = item.summary();
        this.source = truncate(item.source(), 100);
        this.publishedAt = item.publishedAt();
        this.sentimentScore = item.sentimentScore();
        this.sentimentLabel = truncate(item.sentimentLabel(), 30);
        this.fetchedAt = fetchedAt;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSource() {
        return source;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public BigDecimal getSentimentScore() {
        return sentimentScore;
    }

    public String getSentimentLabel() {
        return sentimentLabel;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
