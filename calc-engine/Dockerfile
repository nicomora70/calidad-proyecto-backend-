FROM python:3.15-slim

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
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
