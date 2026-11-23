package co.ritech.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily change of the reference symbol as reported by a market data provider. */
public record MarketQuote(String symbol, LocalDate marketDate, BigDecimal changePct, String source) {
}
