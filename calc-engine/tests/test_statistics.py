from decimal import Decimal

from app.calculations.statistics import (
    SimulationSample,
    calculate_statistics,
    max_drawdown_pct,
)


def test_empty_samples_return_zeros():
    s = calculate_statistics([])
    assert s.count == 0
    assert s.total_capital == Decimal("0.00")


def test_aggregates():
    s = calculate_statistics(
        [
            SimulationSample(Decimal("100000"), Decimal("4")),
            SimulationSample(Decimal("300000"), Decimal("-2")),
        ]
    )
    assert s.count == 2
    assert s.total_capital == Decimal("400000.00")
    assert s.total_profit == Decimal("-2000.00")
    assert s.mean_return_pct == Decimal("1.0000")
    assert s.weighted_return_pct == Decimal("-0.5000")
    assert s.best_return_pct == Decimal("4.0000")
    assert s.worst_return_pct == Decimal("-2.0000")
    assert s.win_rate_pct == Decimal("50.0000")


def test_max_drawdown():
    # 1 -> 1.10 (peak) -> 0.99 -> 1.089 : drawdown = 10%
    assert max_drawdown_pct([Decimal("10"), Decimal("-10"), Decimal("10")]) == Decimal("10.0000")


def test_no_drawdown_when_always_up():
    assert max_drawdown_pct([Decimal("1"), Decimal("2")]) == Decimal("0.0000")
