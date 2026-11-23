package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An already computed simulation. Immutable by design: no setters. */
@Entity
@Table(name = "simulations")
public class Simulation {

    @Id
    private UUID id;

    /** Nullable until authentication lands (Phase 1, migration V2 makes it NOT NULL). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "initial_capital", nullable = false, precision = 18, scale = 2)
    private BigDecimal initialCapital;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "market_date", nullable = false)
    private LocalDate marketDate;

    @Column(name = "market_variation_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal marketVariationPct;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal leverage;

    @Column(name = "return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal returnPct;

    @Column(name = "final_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal finalAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal profit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Simulation() {
    }

    public Simulation(UUID userId, BigDecimal initialCapital, String currency, String symbol,
                      LocalDate marketDate, BigDecimal marketVariationPct, BigDecimal leverage,
                      BigDecimal returnPct, BigDecimal finalAmount, BigDecimal profit,
                      Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.initialCapital = initialCapital;
        this.currency = currency;
        this.symbol = symbol;
        this.marketDate = marketDate;
        this.marketVariationPct = marketVariationPct;
        this.leverage = leverage;
        this.returnPct = returnPct;
        this.finalAmount = finalAmount;
        this.profit = profit;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public String getCurrency() { return currency; }
    public String getSymbol() { return symbol; }
    public LocalDate getMarketDate() { return marketDate; }
    public BigDecimal getMarketVariationPct() { return marketVariationPct; }
    public BigDecimal getLeverage() { return leverage; }
    public BigDecimal getReturnPct() { return returnPct; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public BigDecimal getProfit() { return profit; }
    public Instant getCreatedAt() { return createdAt; }
}
