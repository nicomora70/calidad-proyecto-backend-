package co.ritech.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cached daily variation (one row per symbol and market date). */
@Entity
@Table(name = "market_variations")
public class MarketVariation {

    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "market_date", nullable = false)
    private LocalDate marketDate;

    @Column(name = "change_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal changePct;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected MarketVariation() {}

    public MarketVariation(MarketQuote quote, Instant fetchedAt) {
        this.id = UUID.randomUUID();
        this.symbol = quote.symbol();
        this.marketDate = quote.marketDate();
        this.changePct = quote.changePct();
        this.source = quote.source();
        this.fetchedAt = fetchedAt;
    }

    public void refresh(MarketQuote quote, Instant fetchedAt) {
        this.changePct = quote.changePct();
        this.source = quote.source();
        this.fetchedAt = fetchedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getMarketDate() {
        return marketDate;
    }

    public BigDecimal getChangePct() {
        return changePct;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
