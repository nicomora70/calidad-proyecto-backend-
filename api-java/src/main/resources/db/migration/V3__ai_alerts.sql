-- Phase 2: AI alerts generated asynchronously after each simulation (best-effort).

CREATE TABLE ai_alerts (
    id                UUID PRIMARY KEY,
    simulation_id     UUID          NOT NULL UNIQUE REFERENCES simulations (id),
    user_id           UUID          NOT NULL REFERENCES users (id),
    status            VARCHAR(10)   NOT NULL,   -- PENDING | DONE | FAILED
    title             VARCHAR(200),
    content           TEXT,
    model             VARCHAR(60),
    prompt_version    VARCHAR(20),
    prompt_tokens     INTEGER       NOT NULL DEFAULT 0,
    completion_tokens INTEGER       NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(10,6) NOT NULL DEFAULT 0,
    failure_reason    VARCHAR(300),
    created_at        TIMESTAMPTZ   NOT NULL,
    completed_at      TIMESTAMPTZ
);

CREATE INDEX ix_ai_alerts_user_created ON ai_alerts (user_id, created_at DESC);
CREATE INDEX ix_ai_alerts_status_created ON ai_alerts (status, created_at);
