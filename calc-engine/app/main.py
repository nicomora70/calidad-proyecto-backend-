"""RiTech calc-engine: stateless math service, reachable only from the
internal Docker network. It has no database and no business persistence."""

from dataclasses import asdict

from fastapi import FastAPI

from app.calculations.returns import calculate_return
from app.calculations.statistics import SimulationSample, calculate_statistics
from app.logging_config import RequestIdMiddleware, configure_logging
from app.schemas import (
    ReturnRequest,
    ReturnResponse,
    StatisticsRequest,
    StatisticsResponse,
)

app = FastAPI(title="RiTech calc-engine", version="0.2.0")
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
