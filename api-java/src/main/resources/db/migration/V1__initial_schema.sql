-- RiTech initial schema (Phase 0). Managed by Flyway: never edit an applied migration,
-- add a new V<n>__description.sql instead.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'IT_MANAGER', 'INVESTOR')),
    password_hash   VARCHAR(100) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

-- Email is unique among non-deleted users (soft delete keeps history).
CREATE UNIQUE INDEX ux_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE market_variations (
    id          UUID PRIMARY KEY,
    symbol      VARCHAR(10)   NOT NULL,
    market_date DATE          NOT NULL,
    change_pct  NUMERIC(10,4) NOT NULL,
    source      VARCHAR(30)   NOT NULL,
    fetched_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_market_variations_symbol_date UNIQUE (symbol, market_date)
);

CREATE INDEX ix_market_variations_symbol_fetched ON market_variations (symbol, fetched_at DESC);

CREATE TABLE simulations (
    id                   UUID PRIMARY KEY,
    user_id              UUID REFERENCES users (id),
    initial_capital      NUMERIC(18,2) NOT NULL CHECK (initial_capital > 0),
    currency             VARCHAR(3)    NOT NULL,
    symbol               VARCHAR(10)   NOT NULL,
    market_date          DATE          NOT NULL,
    market_variation_pct NUMERIC(10,4) NOT NULL,
    leverage             NUMERIC(6,2)  NOT NULL,
    return_pct           NUMERIC(10,4) NOT NULL,
    final_amount         NUMERIC(18,2) NOT NULL CHECK (final_amount >= 0),
    profit               NUMERIC(18,2) NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_simulations_user_created ON simulations (user_id, created_at DESC);
CREATE INDEX ix_simulations_created ON simulations (created_at);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_id);
