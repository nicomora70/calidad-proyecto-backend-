-- Phase 4: in-app inbox and e-mail outbox, plus per-user preferences.

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    type            VARCHAR(30)  NOT NULL,   -- AI_ALERT_READY | MARKET_VARIATION | SECURITY_EVENT | LICENSE_EXPIRING
    channel         VARCHAR(10)  NOT NULL,   -- IN_APP | EMAIL
    title           VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL,   -- PENDING | SENT | FAILED (outbox state of e-mails)
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(300),
    dedupe_key      VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ
);

-- Inbox: own in-app notifications, newest first
CREATE INDEX ix_notifications_inbox ON notifications (user_id, channel, created_at DESC);
-- Outbox: pending e-mails due for (re)delivery
CREATE INDEX ix_notifications_outbox ON notifications (status, next_attempt_at) WHERE status = 'PENDING';
-- The same event never notifies a user twice on the same channel
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, channel, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE notification_preferences (
    user_id                 UUID PRIMARY KEY REFERENCES users (id),
    email_enabled           BOOLEAN      NOT NULL,
    in_app_enabled          BOOLEAN      NOT NULL,
    variation_threshold_pct NUMERIC(6,2) NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL
);
