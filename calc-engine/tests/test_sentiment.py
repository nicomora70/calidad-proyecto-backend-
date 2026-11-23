from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from app.calculations.sentiment import calculate_sentiment, label_for
from app.main import app

client = TestClient(app)


@pytest.mark.parametrize(
    ("score", "label"),
    [
        ("-1", "Bearish"),
        ("-0.35", "Bearish"),
        ("-0.3499", "Somewhat-Bearish"),
        ("-0.15", "Somewhat-Bearish"),
        ("-0.1499", "Neutral"),
        ("0", "Neutral"),
        ("0.1499", "Neutral"),
        ("0.15", "Somewhat-Bullish"),
        ("0.3499", "Somewhat-Bullish"),
        ("0.35", "Bullish"),
        ("1", "Bullish"),
    ],
)
def test_label_boundaries(score, label):
    assert label_for(Decimal(score)) == label


def test_empty_day_is_neutral():
    r = calculate_sentiment([])
    assert r.count == 0
    assert r.label == "Neutral"
    assert r.mean_score == Decimal("0.0000")


def test_mean_and_distribution():
    r = calculate_sentiment([Decimal("0.4"), Decimal("0.2"), Decimal("0"), Decimal("-0.3")])
    assert r.count == 4
    assert r.mean_score == Decimal("0.0750")
    assert r.label == "Neutral"
    assert (r.bullish_count, r.neutral_count, r.bearish_count) == (2, 1, 1)


def test_sentiment_endpoint():
    body = client.post("/calculations/sentiment", json={"scores": ["0.5", "0.3"]}).json()
    assert body["mean_score"] == "0.4000"
    assert body["label"] == "Bullish"


def test_sentiment_rejects_out_of_range_score():
    response = client.post("/calculations/sentiment", json={"scores": ["1.5"]})
    assert response.status_code == 422
