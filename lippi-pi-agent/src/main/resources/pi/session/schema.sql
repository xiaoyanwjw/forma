-- Pi SessionStore v1 — sessionId-only（无 tenant_id / user_id）

CREATE TABLE IF NOT EXISTS pi_session (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id          TEXT    NOT NULL,
    title               TEXT,
    source              TEXT    NOT NULL DEFAULT 'api',
    status              TEXT    NOT NULL DEFAULT 'active',
    parent_session_id   TEXT,
    compact_anchor_seq  INTEGER NOT NULL DEFAULT 0,
    last_run_id         TEXT,
    message_count       INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    ended_at            TEXT,
    UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_pi_session_updated
    ON pi_session (updated_at DESC);

CREATE TABLE IF NOT EXISTS pi_session_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT    NOT NULL,
    seq             INTEGER NOT NULL,
    run_id          TEXT,
    role            TEXT    NOT NULL,
    content         TEXT,
    payload_json    TEXT,
    token_estimate  INTEGER,
    created_at      TEXT    NOT NULL,
    UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_pi_session_message_run
    ON pi_session_message (session_id, run_id);
