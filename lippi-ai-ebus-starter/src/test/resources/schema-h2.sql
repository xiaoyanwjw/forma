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
