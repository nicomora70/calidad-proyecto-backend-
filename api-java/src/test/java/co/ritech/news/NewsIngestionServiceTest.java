package co.ritech.news;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.ritech.TestFixtures;
import co.ritech.news.NewsExceptions.NewsQuotaExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsIngestionServiceTest {

    /** Clock that the test can move forward (quota resets per calendar day). */
    static final class MutableClock extends Clock {
        Instant now;
        final ZoneId zone;

        MutableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(now, newZone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class FakeProvider implements NewsProvider {
        List<NewsItem> items = new ArrayList<>();
        boolean limited = true;
        int calls;

        @Override
        public List<NewsItem> fetchLatest(String tickers) {
            calls++;
            return items;
        }

        @Override
        public boolean quotaLimited() {
            return limited;
        }
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T15:00:00Z"), ZoneOffset.UTC);
    private FakeProvider provider;
    private NewsArticleRepository repository;
    private NewsIngestionService service;

    @BeforeEach
    void setUp() {
        provider = new FakeProvider();
        repository = mock(NewsArticleRepository.class);
        when(repository.findExistingUrls(anyCollection())).thenReturn(Set.of());
        service = new NewsIngestionService(
                provider, repository, TestFixtures.properties(), TestFixtures.allModules(), clock);
    }

    private static NewsItem item(String url) {
        return new NewsItem(
                url, "Title " + url, null, "src", Instant.parse("2026-11-20T14:00:00Z"), BigDecimal.ONE, "Bullish");
    }

    @Test
    void storesOnlyUrlsNotSeenBeforeAndDeduplicatesTheBatch() {
        provider.items = List.of(item("https://a"), item("https://b"), item("https://a"));
        when(repository.findExistingUrls(anyCollection())).thenReturn(Set.of("https://b"));

        var result = service.refresh();

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    void emptyFeedDoesNotQueryExistingUrls() {
        var result = service.refresh();

        assertThat(result.inserted()).isZero();
        verify(repository, never()).findExistingUrls(any());
    }

    @Test
    void quotaAllowsExactlyMaxDailyCalls() {
        for (int i = 0; i < 4; i++) { // max-daily-calls = 4
            service.refresh();
        }
        assertThatThrownBy(service::refresh).isInstanceOf(NewsQuotaExceededException.class);
        assertThat(provider.calls).isEqualTo(4);
    }

    @Test
    void quotaResetsOnNextDay() {
        for (int i = 0; i < 4; i++) {
            service.refresh();
        }
        // TestFixtures time zone is America/Bogota (UTC-5): 05:00Z is already the next local day
        clock.now = Instant.parse("2026-11-21T05:00:00Z");

        service.refresh();

        assertThat(provider.calls).isEqualTo(5);
    }

    @Test
    void quotaDoesNotResetBeforeLocalMidnight() {
        for (int i = 0; i < 4; i++) {
            service.refresh();
        }
        clock.now = Instant.parse("2026-11-21T04:59:59Z"); // still 2026-11-20 in Bogota

        assertThatThrownBy(service::refresh).isInstanceOf(NewsQuotaExceededException.class);
    }

    @Test
    void providerWithoutQuotaIsUnlimited() {
        provider.limited = false;
        for (int i = 0; i < 10; i++) {
            service.refresh();
        }
        assertThat(provider.calls).isEqualTo(10);
    }

    @Test
    void failureOnStartupIsLoggedNotThrown() {
        var failing = new NewsProvider() {
            @Override
            public List<NewsItem> fetchLatest(String tickers) {
                throw new NewsExceptions.NewsUnavailableException("down");
            }
        };
        var scheduled = new NewsIngestionService(
                failing, repository, TestFixtures.properties(), TestFixtures.allModules(), clock);

        assertThatCode(scheduled::scheduledRefresh).doesNotThrowAnyException();
    }
}
