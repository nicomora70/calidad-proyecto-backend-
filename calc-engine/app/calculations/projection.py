"""Scenario projection (Enterprise module) by Monte Carlo over the stored daily history.

Each simulated path draws `horizon_days` daily NASDAQ 100 variations with replacement
from the real history (historical bootstrap), applies the RiTech rule (LEVERAGE x the
daily variation) and compounds day by day. A day can lose at most the remaining
capital, so a path that reaches zero stays at zero (same cap as calculate_return).

Scenarios: pessimistic = P10, base = P50 (median), optimistic = P90 of the final amount.
It is an estimate from past data, never a promise of return.
"""

from dataclasses import dataclass
from decimal import ROUND_HALF_EVEN, Decimal

import numpy as np

from app.calculations.returns import CENT, HUNDRED, LEVERAGE, PERCENT_PRECISION

MIN_HISTORY = 5


@dataclass(frozen=True)
class Scenario:
    final_amount: Decimal
    profit: Decimal
    return_pct: Decimal


@dataclass(frozen=True)
class ProjectionResult:
    horizon_days: int
    simulations: int
    sample_size: int
    leverage: Decimal
    pessimistic: Scenario
    base: Scenario
    optimistic: Scenario
    expected_final_amount: Decimal
    probability_of_loss_pct: Decimal


def _money(value: float) -> Decimal:
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_EVEN)


def _scenario(initial_capital: Decimal, final_amount: float) -> Scenario:
    final = _money(final_amount)
    profit = final - initial_capital.quantize(CENT)
    return_pct = (profit / initial_capital * HUNDRED).quantize(
        PERCENT_PRECISION, rounding=ROUND_HALF_EVEN
    )
    return Scenario(final_amount=final, profit=profit, return_pct=return_pct)


def project(
    initial_capital: Decimal,
    daily_variations_pct: list[Decimal],
    horizon_days: int,
    simulations: int = 10_000,
    seed: int | None = None,
    leverage: Decimal = LEVERAGE,
) -> ProjectionResult:
    if initial_capital <= 0:
        raise ValueError("initial_capital must be greater than zero")
    if len(daily_variations_pct) < MIN_HISTORY:
        raise ValueError(f"at least {MIN_HISTORY} daily variations are required")

    rng = np.random.default_rng(seed)
    history = np.array([float(v) for v in daily_variations_pct]) / 100.0
    draws = rng.choice(history, size=(simulations, horizon_days), replace=True)
    daily_factor = np.maximum(0.0, 1.0 + float(leverage) * draws)
    finals = float(initial_capital) * np.prod(daily_factor, axis=1)

    p10, p50, p90 = np.percentile(finals, [10, 50, 90])
    loss_pct = float(np.mean(finals < float(initial_capital))) * 100.0
    return ProjectionResult(
        horizon_days=horizon_days,
        simulations=simulations,
        sample_size=len(daily_variations_pct),
        leverage=leverage,
        pessimistic=_scenario(initial_capital, float(p10)),
        base=_scenario(initial_capital, float(p50)),
        optimistic=_scenario(initial_capital, float(p90)),
        expected_final_amount=_money(float(np.mean(finals))),
        probability_of_loss_pct=Decimal(str(loss_pct)).quantize(
            PERCENT_PRECISION, rounding=ROUND_HALF_EVEN
        ),
    )
