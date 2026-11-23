"""JSON logs (one line per event) with the trace id propagated from the Java API.

The API forwards its X-Request-Id; reusing it here lets one request be followed
across both services' logs.
"""

import json
import logging
import re
import time
import uuid
from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

REQUEST_ID_HEADER = "X-Request-Id"
_VALID_ID = re.compile(r"^[A-Za-z0-9._-]{8,64}$")
trace_id: ContextVar[str | None] = ContextVar("trace_id", default=None)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "@timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "log.level": record.levelname,
            "log.logger": record.name,
            "message": record.getMessage(),
            "service.name": "calc-engine",
            "traceId": trace_id.get(),
        }
        entry.update(getattr(record, "fields", {}))
        if record.exc_info:
            entry["error.stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(entry, default=str)


def configure_logging() -> logging.Logger:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    logger = logging.getLogger("calc_engine")
    logger.handlers = [handler]
    logger.setLevel(logging.INFO)
    logger.propagate = False
    return logger


class RequestIdMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, logger: logging.Logger):
        super().__init__(app)
        self._logger = logger

    async def dispatch(self, request: Request, call_next) -> Response:
        incoming = request.headers.get(REQUEST_ID_HEADER)
        request_id = incoming if incoming and _VALID_ID.match(incoming) else str(uuid.uuid4())
        token = trace_id.set(request_id)
        started = time.perf_counter()
        try:
            response = await call_next(request)
            response.headers[REQUEST_ID_HEADER] = request_id
            if request.url.path != "/health":
                self._logger.info(
                    "request completed",
                    extra={
                        "fields": {
                            "http.method": request.method,
                            "url.path": request.url.path,
                            "http.status_code": response.status_code,
                            "duration_ms": round((time.perf_counter() - started) * 1000, 2),
                        }
                    },
                )
            return response
        finally:
            trace_id.reset(token)
