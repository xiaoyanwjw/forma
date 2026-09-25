-- Identity: 用户账号（库内 BIGINT 自增 PK；对外/业务 ID = biz_id UUID；时间戳按 JDBC 会话时区）
CREATE TABLE IF NOT EXISTS ebus_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id        VARCHAR(36)  NOT NULL COMMENT '业务/对外用户 ID（UUID）',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    email         VARCHAR(255) NOT NULL COMMENT '邮箱',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_user_biz (biz_id),
    UNIQUE KEY uk_ebus_user_username (username),
    UNIQUE KEY uk_ebus_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 用户账号';
