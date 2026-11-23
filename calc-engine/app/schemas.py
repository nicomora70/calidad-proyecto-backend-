"""Request/response contracts of the calc-engine (consumed by the Java API)."""

from decimal import Decimal
from typing import Annotated

from pydantic import BaseModel, Field


class ReturnRequest(BaseModel):
    initial_capital: Decimal = Field(gt=0)
    market_variation_pct: Decimal = Field(ge=-100, le=100)


class ReturnResponse(BaseModel):
    initial_capital: Decimal
    market_variation_pct: Decimal
    leverage: Decimal
    return_pct: Decimal
    final_amount: Decimal
    profit: Decimal
    capped_loss: bool


class SampleIn(BaseModel):
    initial_capital: Decimal = Field(gt=0)
    return_pct: Decimal


class StatisticsRequest(BaseModel):
    samples: list[SampleIn]


class StatisticsResponse(BaseModel):
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


class SentimentRequest(BaseModel):
    scores: list[Annotated[Decimal, Field(ge=-1, le=1)]]


class SentimentResponse(BaseModel):
    count: int
    mean_score: Decimal
    label: str
    bullish_count: int
    neutral_count: int
    bearish_count: int


class ProjectionRequest(BaseModel):
    initial_capital: Decimal = Field(gt=0)
    daily_variations_pct: list[Annotated[Decimal, Field(ge=-100, le=100)]] = Field(min_length=5)
    horizon_days: int = Field(ge=1, le=252)
    simulations: int = Field(default=10_000, ge=1_000, le=50_000)
    seed: int | None = None


class ScenarioOut(BaseModel):
    final_amount: Decimal
    profit: Decimal
    return_pct: Decimal


class ProjectionResponse(BaseModel):
    horizon_days: int
    simulations: int
    sample_size: int
    leverage: Decimal
    pessimistic: ScenarioOut
    base: ScenarioOut
    optimistic: ScenarioOut
    expected_final_amount: Decimal
    probability_of_loss_pct: Decimal
