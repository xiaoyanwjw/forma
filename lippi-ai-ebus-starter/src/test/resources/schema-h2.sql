CREATE TABLE IF NOT EXISTS ebus_user (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_ebus_user_username UNIQUE (username),
    CONSTRAINT uk_ebus_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS ebus_credit_account (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id          VARCHAR(36)  NOT NULL,
    tier             VARCHAR(16)  NOT NULL,
    balance          INT          NOT NULL,
    reserved         INT          NOT NULL DEFAULT 0,
    period_anchor_at TIMESTAMP    NOT NULL,
    next_reset_at    TIMESTAMP    NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_ebus_credit_account_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS ebus_credit_hold (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    amount     INT          NOT NULL DEFAULT 1,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS ebus_credit_tier_change (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    account_id       VARCHAR(36)  NOT NULL,
    target_user_id   VARCHAR(36)  NOT NULL,
    operator_user_id VARCHAR(36)  NOT NULL,
    from_tier        VARCHAR(16)  NOT NULL,
    to_tier          VARCHAR(16)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS ebus_generation_run (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    hold_id      VARCHAR(36)  NOT NULL,
    session_id   VARCHAR(36)  NOT NULL,
    artifact_ref VARCHAR(36)  NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ebus_generation_run_user ON ebus_generation_run (user_id);
CREATE INDEX IF NOT EXISTS idx_ebus_generation_run_hold ON ebus_generation_run (hold_id);
CREATE INDEX IF NOT EXISTS idx_ebus_generation_run_session ON ebus_generation_run (session_id);

CREATE TABLE IF NOT EXISTS pi_session (
    session_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)  NULL,
    title               VARCHAR(255) NULL,
    source              VARCHAR(32)  NOT NULL DEFAULT 'api',
    status              VARCHAR(16)  NOT NULL DEFAULT 'active',
    parent_session_id   VARCHAR(64)  NULL,
    compact_anchor_seq  BIGINT       NOT NULL DEFAULT 0,
    last_run_id         VARCHAR(64)  NULL,
    message_count       INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pi_session_updated ON pi_session (updated_at);
CREATE INDEX IF NOT EXISTS idx_pi_session_parent ON pi_session (parent_session_id);

CREATE TABLE IF NOT EXISTS pi_session_entry (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id   VARCHAR(64)  NOT NULL,
    seq          BIGINT       NOT NULL,
    entry_type   VARCHAR(32)  NOT NULL DEFAULT 'message',
    parent_id    VARCHAR(36)  NULL,
    run_id       VARCHAR(64)  NULL,
    payload      CLOB         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pi_session_entry_seq UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_pi_session_entry_run ON pi_session_entry (session_id, run_id);

CREATE TABLE IF NOT EXISTS pi_graph_checkpoint (
    run_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    checkpoint_id   VARCHAR(64)  NULL,
    graph_state     CLOB         NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pi_graph_checkpoint_expires ON pi_graph_checkpoint (expires_at);
