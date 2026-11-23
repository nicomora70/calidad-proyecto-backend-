package co.ritech.news;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.news.NewsIngestionService.RefreshResult;
import co.ritech.news.NewsService.DailySentiment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/news")
@Tag(name = "News")
@SecurityRequirement(name = "bearer")
public class NewsController {

    private final NewsService news;
    private final NewsIngestionService ingestion;

    public NewsController(NewsService news, NewsIngestionService ingestion) {
        this.news = news;
        this.ingestion = ingestion;
    }

    public record NewsResponse(
            UUID id,
            String url,
            String title,
            String summary,
            String source,
            Instant publishedAt,
            BigDecimal sentimentScore,
            String sentimentLabel) {

        static NewsResponse from(NewsArticle a) {
            return new NewsResponse(
                    a.getId(),
                    a.getUrl(),
                    a.getTitle(),
                    a.getSummary(),
                    a.getSource(),
                    a.getPublishedAt(),
                    a.getSentimentScore(),
                    a.getSentimentLabel());
        }
    }

    @GetMapping
    @Operation(summary = "Market news feed, filterable by publication date (inclusive) and sentiment label")
    public Page<NewsResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String sentiment,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return news.search(from, to, sentiment, pageable).map(NewsResponse::from);
    }

    @GetMapping("/sentiment")
    @Operation(summary = "Sentiment indicator of a day (default: today in the installation time zone)")
    public DailySentiment sentiment(@RequestParam(required = false) LocalDate date) {
        return news.dailySentiment(date);
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Download the news feed now (ADMIN). Counts against the daily quota")
    public RefreshResult refresh() {
        return ingestion.refresh();
    }
}
