"""RiTech calc-engine: stateless math service, reachable only from the
internal Docker network. It has no database and no business persistence."""

from dataclasses import asdict

from fastapi import FastAPI

from app.calculations.projection import project
from app.calculations.returns import calculate_return
from app.calculations.sentiment import calculate_sentiment
from app.calculations.statistics import SimulationSample, calculate_statistics
from app.logging_config import RequestIdMiddleware, configure_logging
from app.schemas import (
    ProjectionRequest,
    ProjectionResponse,
    ReturnRequest,
    ReturnResponse,
    SentimentRequest,
    SentimentResponse,
    StatisticsRequest,
    StatisticsResponse,
)

app = FastAPI(title="RiTech calc-engine", version="0.5.0")
app.add_middleware(RequestIdMiddleware, logger=configure_logging())


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "calc-engine"}


@app.post("/calculations/returns", response_model=ReturnResponse)
def returns(request: ReturnRequest) -> ReturnResponse:
    result = calculate_return(request.initial_capital, request.market_variation_pct)
    return ReturnResponse(**asdict(result))


@app.post("/calculations/statistics", response_model=StatisticsResponse)
def statistics(request: StatisticsRequest) -> StatisticsResponse:
    samples = [SimulationSample(s.initial_capital, s.return_pct) for s in request.samples]
    return StatisticsResponse(**asdict(calculate_statistics(samples)))


@app.post("/calculations/sentiment", response_model=SentimentResponse)
def sentiment(request: SentimentRequest) -> SentimentResponse:
    return SentimentResponse(**asdict(calculate_sentiment(request.scores)))


@app.post("/calculations/projection", response_model=ProjectionResponse)
def projection(request: ProjectionRequest) -> ProjectionResponse:
    result = project(
        request.initial_capital,
        request.daily_variations_pct,
        request.horizon_days,
        simulations=request.simulations,
        seed=request.seed,
    )
    return ProjectionResponse(**asdict(result))
