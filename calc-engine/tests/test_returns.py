from decimal import Decimal

import pytest

from app.calculations.returns import calculate_return


def test_positive_variation_doubles_gain():
    r = calculate_return(Decimal("1000000"), Decimal("2"))
    assert r.return_pct == Decimal("4.0000")
    assert r.final_amount == Decimal("1040000.00")
    assert r.profit == Decimal("40000.00")
    assert not r.capped_loss


def test_negative_variation_doubles_loss():
    r = calculate_return(Decimal("1000000"), Decimal("-2"))
    assert r.return_pct == Decimal("-4.0000")
    assert r.final_amount == Decimal("960000.00")
    assert r.profit == Decimal("-40000.00")


def test_zero_variation_keeps_capital():
    r = calculate_return(Decimal("150000"), Decimal("0"))
    assert r.final_amount == Decimal("150000.00")
    assert r.profit == Decimal("0.00")


def test_loss_is_capped_at_capital():
    r = calculate_return(Decimal("100000"), Decimal("-60"))
    assert r.final_amount == Decimal("0.00")
    assert r.profit == Decimal("-100000.00")
    assert r.capped_loss


def test_rounding_half_even_to_cents():
    r = calculate_return(Decimal("100000.005"), Decimal("0"))
    assert r.final_amount == Decimal("100000.00")


def test_non_positive_capital_is_rejected():
    with pytest.raises(ValueError):
        calculate_return(Decimal("0"), Decimal("1"))
