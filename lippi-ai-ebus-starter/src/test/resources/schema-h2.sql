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
