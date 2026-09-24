-- Identity: 用户账号（UUID PK；用户名/邮箱唯一；密码仅存哈希；时间戳 UTC）
CREATE TABLE IF NOT EXISTS ebus_user (
    id            VARCHAR(36)  NOT NULL COMMENT '用户 ID（UUID）',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    email         VARCHAR(255) NOT NULL COMMENT '邮箱',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_user_username (username),
    UNIQUE KEY uk_ebus_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 用户账号';
