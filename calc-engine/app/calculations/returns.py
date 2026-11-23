"""Investment return calculation for RiTech.

Business rule: RiTech gains or loses exactly LEVERAGE times the daily
variation of the NASDAQ 100 (symmetric model). Losses are capped at the
invested capital: the final amount can never be negative.
"""

from dataclasses import dataclass
from decimal import ROUND_HALF_EVEN, Decimal

LEVERAGE = Decimal("2")
CENT = Decimal("0.01")
PERCENT_PRECISION = Decimal("0.0001")
HUNDRED = Decimal("100")
ZERO = Decimal("0")


@dataclass(frozen=True)
class ReturnResult:
    initial_capital: Decimal
    market_variation_pct: Decimal
    leverage: Decimal
    return_pct: Decimal
    final_amount: Decimal
    profit: Decimal
    capped_loss: bool


def calculate_return(
    initial_capital: Decimal,
    market_variation_pct: Decimal,
    leverage: Decimal = LEVERAGE,
) -> ReturnResult:
    if initial_capital <= ZERO:
        raise ValueError("initial_capital must be greater than zero")

    return_pct = (market_variation_pct * leverage).quantize(
        PERCENT_PRECISION, rounding=ROUND_HALF_EVEN
    )
    raw_final = initial_capital * (Decimal("1") + return_pct / HUNDRED)

    capped_loss = raw_final < ZERO
    final_amount = ZERO if capped_loss else raw_final
    final_amount = final_amount.quantize(CENT, rounding=ROUND_HALF_EVEN)
    capital = initial_capital.quantize(CENT, rounding=ROUND_HALF_EVEN)
    profit = (final_amount - capital).quantize(CENT, rounding=ROUND_HALF_EVEN)

    return ReturnResult(
        initial_capital=capital,
        market_variation_pct=market_variation_pct,
        leverage=leverage,
        return_pct=return_pct,
        final_amount=final_amount,
        profit=profit,
        capped_loss=capped_loss,
    )
