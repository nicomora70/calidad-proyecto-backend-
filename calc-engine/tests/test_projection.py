from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from app.calculations.projection import project
from app.main import app

client = TestClient(app)
CAPITAL = Decimal("1000000")


def history(*values: str) -> list[Decimal]:
    return [Decimal(v) for v in values]


def test_flat_history_keeps_capital_in_every_scenario():
    r = project(
        CAPITAL, history("0", "0", "0", "0", "0"), horizon_days=20, simulations=1000, seed=1
    )
    assert (
        r.pessimistic.final_amount
        == r.base.final_amount
        == r.optimistic.final_amount
        == Decimal("1000000.00")
    )
    assert r.probability_of_loss_pct == Decimal("0.0000")


def test_scenarios_are_ordered_and_deterministic_with_seed():
    h = history("1.2", "-0.8", "0.5", "-1.5", "2.0", "0.1", "-0.3")
    a = project(CAPITAL, h, horizon_days=60, simulations=5000, seed=42)
    b = project(CAPITAL, h, horizon_days=60, simulations=5000, seed=42)
    assert a == b
    assert a.pessimistic.final_amount <= a.base.final_amount <= a.optimistic.final_amount
    assert Decimal("0") < a.probability_of_loss_pct < Decimal("100")


def test_single_day_applies_leverage():
    # Every draw is +1 % -> 2x leverage -> +2 % in one day
    r = project(CAPITAL, history("1", "1", "1", "1", "1"), horizon_days=1, simulations=1000, seed=3)
    assert r.base.final_amount == Decimal("1020000.00")
    assert r.base.return_pct == Decimal("2.0000")


def test_loss_is_capped_at_the_invested_capital():
    # -60 % x 2 = -120 % in a day: the path goes to zero and stays there, never negative
    r = project(
        CAPITAL,
        history("-60", "-60", "-60", "-60", "-60"),
        horizon_days=3,
        simulations=1000,
        seed=7,
    )
    assert r.optimistic.final_amount == Decimal("0.00")
    assert r.probability_of_loss_pct == Decimal("100.0000")


@pytest.mark.parametrize("size", [0, 4])
def test_rejects_short_history(size):
    with pytest.raises(ValueError):
        project(CAPITAL, [Decimal("1")] * size, horizon_days=10)


def test_projection_endpoint():
    body = client.post(
        "/calculations/projection",
        json={
            "initial_capital": "1000000",
            "daily_variations_pct": ["1", "-1", "0.5", "-0.5", "0.2"],
            "horizon_days": 20,
            "simulations": 2000,
            "seed": 11,
        },
    ).json()
    assert body["sample_size"] == 5
    assert body["leverage"] == "2"
    assert Decimal(body["pessimistic"]["final_amount"]) <= Decimal(
        body["optimistic"]["final_amount"]
    )


@pytest.mark.parametrize(
    ("field", "value"),
    [("horizon_days", 0), ("horizon_days", 253), ("simulations", 999), ("simulations", 50_001)],
)
def test_projection_endpoint_validates_bounds(field, value):
    payload = {
        "initial_capital": "1000000",
        "daily_variations_pct": ["1", "-1", "0.5", "-0.5", "0.2"],
        "horizon_days": 20,
        "simulations": 2000,
    }
    payload[field] = value
    assert client.post("/calculations/projection", json=payload).status_code == 422


def test_projection_endpoint_accepts_bounds():
    payload = {
        "initial_capital": "1000000",
        "daily_variations_pct": ["1", "-1", "0.5", "-0.5", "0.2"],
        "horizon_days": 252,
        "simulations": 1000,
    }
    assert client.post("/calculations/projection", json=payload).status_code == 200


def test_projection_endpoint_rejects_short_history():
    payload = {"initial_capital": "1000000", "daily_variations_pct": ["1", "2"], "horizon_days": 5}
    assert client.post("/calculations/projection", json=payload).status_code == 422
