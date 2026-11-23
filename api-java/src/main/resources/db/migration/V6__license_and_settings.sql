-- Phase 5: signed licenses (renewal history, newest in force) and installation settings.

CREATE TABLE licenses (
    id           UUID PRIMARY KEY,
    license_id   VARCHAR(60)  NOT NULL,
    company      VARCHAR(150) NOT NULL,
    plan         VARCHAR(20)  NOT NULL,   -- BASIC | PROFESSIONAL | ENTERPRISE
    max_users    INTEGER      NOT NULL,
    issued_at    DATE         NOT NULL,
    expires_at   DATE         NOT NULL,
    payload      TEXT         NOT NULL,   -- base64 JSON exactly as signed
    signature    TEXT         NOT NULL,   -- base64 Ed25519 signature of the payload
    installed_at TIMESTAMPTZ  NOT NULL,
    installed_by UUID REFERENCES users (id)
);

CREATE INDEX ix_licenses_installed ON licenses (installed_at DESC);

CREATE TABLE settings (
    setting_key   VARCHAR(80) PRIMARY KEY,
    setting_value TEXT,
    updated_at    TIMESTAMPTZ NOT NULL
);
