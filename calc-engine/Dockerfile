FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /srv

# Dev dependencies (pytest) are included so tests can run inside the container.
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements-dev.txt

COPY pytest.ini ./
COPY app ./app
COPY tests ./tests

RUN useradd --create-home --uid 10001 engine
USER engine

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD ["python", "-c", "import os, urllib.request; urllib.request.urlopen(f'http://localhost:{os.environ.get(\"PORT\", \"8000\")}/health')"]

# HOST=:: only where the private network is IPv6-only (legacy Railway environments):
# asyncio binds "::" as IPv6-only, so IPv4 clients (docker compose) could not connect.
CMD ["sh", "-c", "exec uvicorn app.main:app --host ${HOST:-0.0.0.0} --port ${PORT:-8000}"]
