package co.ritech.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published inside the transaction that stores a variation fetched from the provider. */
public record MarketVariationFetched(String symbol, LocalDate marketDate, BigDecimal changePct) {}
