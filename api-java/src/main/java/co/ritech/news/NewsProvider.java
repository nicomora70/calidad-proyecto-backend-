package co.ritech.news;

import java.util.List;

/** Source of market news. Implementations are swappable with NEWS_PROVIDER. */
public interface NewsProvider {

    /**
     * @param tickers comma-separated symbols (e.g. "QQQ")
     * @throws NewsUnavailableException if the feed cannot be obtained
     */
    List<NewsItem> fetchLatest(String tickers);

    /** Whether each call consumes a limited daily quota. */
    default boolean quotaLimited() {
        return true;
    }
}
