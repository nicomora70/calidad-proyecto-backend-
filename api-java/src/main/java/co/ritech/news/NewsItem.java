package co.ritech.news;

import java.math.BigDecimal;
import java.time.Instant;

/** One article as returned by a provider, before it is stored. */
public record NewsItem(
        String url,
        String title,
        String summary,
        String source,
        Instant publishedAt,
        BigDecimal sentimentScore,
        String sentimentLabel) {}
