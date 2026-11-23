"""Aggregate statistics over a set of simulations (dashboard figures)."""

import statistics as pystats
from dataclasses import dataclass
from decimal import ROUND_HALF_EVEN, Decimal

from app.calculations.returns import CENT, HUNDRED, PERCENT_PRECISION, ZERO


@dataclass(frozen=True)
class SimulationSample:
    initial_capital: Decimal
    return_pct: Decimal


@dataclass(frozen=True)
class StatisticsResult:
    count: int
    total_capital: Decimal
    total_profit: Decimal
    mean_return_pct: Decimal
    weighted_return_pct: Decimal
    stdev_return_pct: Decimal
    best_return_pct: Decimal
    worst_return_pct: Decimal
    max_drawdown_pct: Decimal
    win_rate_pct: Decimal


def _pct(value: Decimal) -> Decimal:
    return Decimal(value).quantize(PERCENT_PRECISION, rounding=ROUND_HALF_EVEN)


def _money(value: Decimal) -> Decimal:
    return Decimal(value).quantize(CENT, rounding=ROUND_HALF_EVEN)


def max_drawdown_pct(returns_pct: list[Decimal]) -> Decimal:
    """Largest peak-to-trough drop (as a positive %) of the equity curve
    obtained by compounding the returns in chronological order."""
    equity = Decimal("1")
    peak = equity
    worst = ZERO
    for r in returns_pct:
        equity = max(ZERO, equity * (Decimal("1") + r / HUNDRED))
        peak = max(peak, equity)
        if peak > ZERO:
            worst = max(worst, (peak - equity) / peak * HUNDRED)
    return _pct(worst)


def calculate_statistics(samples: list[SimulationSample]) -> StatisticsResult:
    if not samples:
        return StatisticsResult(
            count=0,
            total_capital=_money(ZERO),
            total_profit=_money(ZERO),
            mean_return_pct=_pct(ZERO),
            weighted_return_pct=_pct(ZERO),
            stdev_return_pct=_pct(ZERO),
            best_return_pct=_pct(ZERO),
            worst_return_pct=_pct(ZERO),
            max_drawdown_pct=_pct(ZERO),
            win_rate_pct=_pct(ZERO),
        )

    returns = [s.return_pct for s in samples]
    total_capital = sum((s.initial_capital for s in samples), ZERO)
    total_profit = sum((s.initial_capital * s.return_pct / HUNDRED for s in samples), ZERO)
    weighted = total_profit / total_capital * HUNDRED if total_capital > ZERO else ZERO
    stdev = pystats.stdev(returns) if len(returns) > 1 else ZERO
    wins = sum(1 for r in returns if r > ZERO)

    return StatisticsResult(
        count=len(samples),
        total_capital=_money(total_capital),
        total_profit=_money(total_profit),
        mean_return_pct=_pct(pystats.mean(returns)),
        weighted_return_pct=_pct(weighted),
        stdev_return_pct=_pct(stdev),
        best_return_pct=_pct(max(returns)),
        worst_return_pct=_pct(min(returns)),
        max_drawdown_pct=max_drawdown_pct(returns),
        win_rate_pct=_pct(Decimal(wins) / Decimal(len(returns)) * HUNDRED),
    )
