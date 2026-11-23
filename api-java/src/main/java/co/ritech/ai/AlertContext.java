package co.ritech.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.ritech.simulation.Simulation;

/** Simulation data and recent stored headlines the alert generator works with (a snapshot, safe to use from another thread). */
public record AlertContext(
        String symbol,
        LocalDate marketDate,
        BigDecimal variationPct,
        BigDecimal leverage,
        BigDecimal returnPct,
        BigDecimal initialCapital,
        String currency,
        BigDecimal finalAmount,
        BigDecimal profit,
        List<String> headlines) {

    public static AlertContext from(Simulation s, List<String> headlines) {
        return new AlertContext(
                s.getSymbol(),
                s.getMarketDate(),
                s.getMarketVariationPct(),
                s.getLeverage(),
                s.getReturnPct(),
                s.getInitialCapital(),
                s.getCurrency(),
                s.getFinalAmount(),
                s.getProfit(),
                List.copyOf(headlines));
    }
}
