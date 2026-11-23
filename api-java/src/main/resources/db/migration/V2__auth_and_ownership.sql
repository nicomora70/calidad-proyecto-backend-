-- Phase 1: authentication, account lockout, refresh tokens and simulation ownership.

ALTER TABLE users
    ADD COLUMN failed_attempts      INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ,
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN last_login_at        TIMESTAMPTZ;

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex; the raw token is never stored
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- Phase 0 simulations had no owner (public demo endpoint). They are discarded
-- so that every simulation belongs to a user from now on.
DELETE FROM simulations WHERE user_id IS NULL;
ALTER TABLE simulations ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE audit_log
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(300);

CREATE INDEX ix_audit_log_action ON audit_log (action);
