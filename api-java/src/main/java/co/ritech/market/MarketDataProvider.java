package co.ritech.market;

/** Source of the daily NASDAQ 100 variation. Implementations are swappable per installation. */
public interface MarketDataProvider {

    /**
     * @throws MarketDataUnavailableException if the quote cannot be obtained
     */
    MarketQuote fetchLatestQuote(String symbol);
}
