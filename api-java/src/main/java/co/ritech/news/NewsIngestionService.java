package co.ritech.news;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import co.ritech.config.RitechProperties;
import co.ritech.license.LicenseModel.Module;
import co.ritech.license.LicenseService;
import co.ritech.news.NewsExceptions.NewsQuotaExceededException;

/**
 * Downloads the news feed every NEWS_REFRESH_CRON and stores the articles not seen before.
 * Calls to a quota-limited provider are capped per day (NEWS_MAX_DAILY_CALLS) so the shared
 * Alpha Vantage quota is left for market data. The counter is in memory: one API instance per
 * client installation, and a restart at most grants a few extra calls.
 * The method is synchronized so the schedule and a manual refresh never insert the same URL twice.
 */
@Service
public class NewsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestionService.class);

    private final NewsProvider provider;
    private final NewsArticleRepository repository;
    private final RitechProperties properties;
    private final LicenseService license;
    private final Clock clock;

    private LocalDate quotaDay;
    private int callsToday;

    public NewsIngestionService(
            NewsProvider provider,
            NewsArticleRepository repository,
            RitechProperties properties,
            LicenseService license,
            Clock clock) {
        this.provider = provider;
        this.repository = repository;
        this.properties = properties;
        this.license = license;
        this.clock = clock;
    }

    public record RefreshResult(int fetched, int inserted) {}

    @Scheduled(cron = "${ritech.news.refresh-cron}", zone = "${ritech.timezone}")
    public void scheduledRefresh() {
        refreshQuietly();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        if (properties.news().refreshOnStartup()) {
            refreshQuietly();
        }
    }

    public synchronized RefreshResult refresh() {
        consumeQuota();
        List<NewsItem> items = provider.fetchLatest(properties.news().tickers());

        Map<String, NewsItem> byUrl = new LinkedHashMap<>();
        items.forEach(item -> byUrl.putIfAbsent(item.url(), item));
        Set<String> existing = byUrl.isEmpty() ? Set.of() : repository.findExistingUrls(byUrl.keySet());
        var now = clock.instant();
        List<NewsArticle> fresh = byUrl.values().stream()
                .filter(item -> !existing.contains(item.url()))
                .map(item -> new NewsArticle(item, now))
                .toList();
        repository.saveAll(fresh);

        log.info("News refresh: {} fetched, {} new", items.size(), fresh.size());
        return new RefreshResult(items.size(), fresh.size());
    }

    private void refreshQuietly() {
        if (!license.hasModule(Module.NEWS)) {
            return;
        }
        try {
            refresh();
        } catch (RuntimeException ex) {
            log.warn("News refresh failed: {}", ex.getMessage());
        }
    }

    private void consumeQuota() {
        if (!provider.quotaLimited()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            callsToday = 0;
        }
        int max = properties.news().maxDailyCalls();
        if (callsToday >= max) {
            throw new NewsQuotaExceededException(max);
        }
        callsToday++;
    }
}
