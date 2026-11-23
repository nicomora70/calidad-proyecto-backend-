package co.ritech.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class SimulationDtos {

    private SimulationDtos() {}

    /** The owner is always the authenticated user (taken from the access token). */
    public record CreateSimulationRequest(
            @NotNull @Positive @Digits(integer = 16, fraction = 2)
            BigDecimal initialCapital) {}

    public record SimulationResponse(
            UUID id,
            UUID userId,
            BigDecimal initialCapital,
            String currency,
            String symbol,
            LocalDate marketDate,
            BigDecimal marketVariationPct,
            BigDecimal leverage,
            BigDecimal returnPct,
            BigDecimal finalAmount,
            BigDecimal profit,
            Instant createdAt) {

        public static SimulationResponse from(Simulation s) {
            return new SimulationResponse(
                    s.getId(),
                    s.getUserId(),
                    s.getInitialCapital(),
                    s.getCurrency(),
                    s.getSymbol(),
                    s.getMarketDate(),
                    s.getMarketVariationPct(),
                    s.getLeverage(),
                    s.getReturnPct(),
                    s.getFinalAmount(),
                    s.getProfit(),
                    s.getCreatedAt());
        }
    }
}
