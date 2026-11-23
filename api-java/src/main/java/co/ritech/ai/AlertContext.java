package co.ritech.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.ritech.simulation.Simulation;

/** Simulation data the alert generator works with (a snapshot, safe to use from another thread). */
public record AlertContext(
        String symbol,
        LocalDate marketDate,
        BigDecimal variationPct,
        BigDecimal leverage,
        BigDecimal returnPct,
        BigDecimal initialCapital,
        String currency,
        BigDecimal finalAmount,
        BigDecimal profit) {

    public static AlertContext from(Simulation s) {
        return new AlertContext(
                s.getSymbol(),
                s.getMarketDate(),
                s.getMarketVariationPct(),
                s.getLeverage(),
                s.getReturnPct(),
                s.getInitialCapital(),
                s.getCurrency(),
                s.getFinalAmount(),
                s.getProfit());
    }
}
