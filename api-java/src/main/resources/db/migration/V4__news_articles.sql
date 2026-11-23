-- Phase 3: market news ingested on a schedule, deduplicated by URL.

CREATE TABLE news_articles (
    id              UUID PRIMARY KEY,
    url             VARCHAR(1000) NOT NULL UNIQUE,
    title           VARCHAR(500)  NOT NULL,
    summary         TEXT,
    source          VARCHAR(100),
    published_at    TIMESTAMPTZ   NOT NULL,
    sentiment_score NUMERIC(6,4),             -- [-1, 1], Alpha Vantage overall_sentiment_score
    sentiment_label VARCHAR(30),
    fetched_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_news_articles_published ON news_articles (published_at DESC);
