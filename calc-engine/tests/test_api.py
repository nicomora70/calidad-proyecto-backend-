import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    assert client.get("/health").json()["status"] == "UP"


def test_request_id_is_propagated_from_caller():
    response = client.get("/health", headers={"X-Request-Id": "trace-abc-123"})
    assert response.headers["X-Request-Id"] == "trace-abc-123"


def test_invalid_request_id_is_replaced():
    response = client.get("/health", headers={"X-Request-Id": "bad id with spaces"})
    assert response.headers["X-Request-Id"] != "bad id with spaces"
    assert len(response.headers["X-Request-Id"]) == 36  # generated UUID


def test_returns_endpoint():
    body = client.post(
        "/calculations/returns",
        json={"initial_capital": "1000000", "market_variation_pct": "1.5"},
    ).json()
    assert body["return_pct"] == "3.0000"
    assert body["final_amount"] == "1030000.00"


def test_returns_rejects_invalid_capital():
    response = client.post(
        "/calculations/returns",
        json={"initial_capital": "0", "market_variation_pct": "1"},
    )
    assert response.status_code == 422


def test_statistics_endpoint():
    body = client.post(
        "/calculations/statistics",
        json={"samples": [{"initial_capital": "100000", "return_pct": "2"}]},
    ).json()
    assert body["count"] == 1
    assert body["total_profit"] == "2000.00"


# Boundary values of market_variation_pct (29119-4): [-100, 100] accepted, just outside rejected
@pytest.mark.parametrize(
    ("variation", "status", "final_amount"),
    [
        ("100", 200, "3000000.00"),  # +100 % -> return +200 %
        ("-100", 200, "0.00"),  # -100 % -> loss capped at capital
        ("100.01", 422, None),
        ("-100.01", 422, None),
    ],
)
def test_returns_variation_boundaries(variation, status, final_amount):
    response = client.post(
        "/calculations/returns",
        json={"initial_capital": "1000000", "market_variation_pct": variation},
    )
    assert response.status_code == status
    if final_amount is not None:
        assert response.json()["final_amount"] == final_amount
