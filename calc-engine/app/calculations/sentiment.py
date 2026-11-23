"""Daily news sentiment indicator from per-article scores in [-1, 1].

Labels follow the Alpha Vantage NEWS_SENTIMENT scale so the aggregate and the
per-article labels stored by the API read the same way.
"""

from dataclasses import dataclass
from decimal import ROUND_HALF_EVEN, Decimal

SCORE_PRECISION = Decimal("0.0001")
BEARISH = Decimal("-0.35")
SOMEWHAT_BEARISH = Decimal("-0.15")
SOMEWHAT_BULLISH = Decimal("0.15")
BULLISH = Decimal("0.35")


@dataclass(frozen=True)
class SentimentResult:
    count: int
    mean_score: Decimal
    label: str
    bullish_count: int
    neutral_count: int
    bearish_count: int


def label_for(score: Decimal) -> str:
    if score <= BEARISH:
        return "Bearish"
    if score <= SOMEWHAT_BEARISH:
        return "Somewhat-Bearish"
    if score < SOMEWHAT_BULLISH:
        return "Neutral"
    if score < BULLISH:
        return "Somewhat-Bullish"
    return "Bullish"


def calculate_sentiment(scores: list[Decimal]) -> SentimentResult:
    if not scores:
        return SentimentResult(0, Decimal("0").quantize(SCORE_PRECISION), "Neutral", 0, 0, 0)

    mean = (sum(scores, Decimal("0")) / Decimal(len(scores))).quantize(
        SCORE_PRECISION, rounding=ROUND_HALF_EVEN
    )
    bullish = sum(1 for s in scores if s >= SOMEWHAT_BULLISH)
    bearish = sum(1 for s in scores if s <= SOMEWHAT_BEARISH)
    return SentimentResult(
        count=len(scores),
        mean_score=mean,
        label=label_for(mean),
        bullish_count=bullish,
        neutral_count=len(scores) - bullish - bearish,
        bearish_count=bearish,
    )
