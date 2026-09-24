-- CreditLedger: 账户 + 预占（UUID PK；档位/余额/锚点/下次重置；时间戳 UTC）
CREATE TABLE IF NOT EXISTS ebus_credit_account (
    id               VARCHAR(36)  NOT NULL COMMENT '账本 ID（UUID）',
    user_id          VARCHAR(36)  NOT NULL COMMENT '用户 ID（UUID）',
    tier             VARCHAR(16)  NOT NULL COMMENT '套餐档：FREE / PRO / PLUS',
    balance          INT          NOT NULL COMMENT '已结算剩余额度（预占占用见 reserved）',
    reserved         INT          NOT NULL DEFAULT 0 COMMENT '活跃预占占用之和',
    period_anchor_at DATETIME(3)  NOT NULL COMMENT '订阅周期锚点 UTC（注册或补偿建账时刻）',
    next_reset_at    DATETIME(3)  NOT NULL COMMENT '下次月重置时刻 UTC',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at       DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_credit_account_user (user_id),
    KEY idx_ebus_credit_account_next_reset (next_reset_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分账本账户';

CREATE TABLE IF NOT EXISTS ebus_credit_hold (
    id         VARCHAR(36)  NOT NULL COMMENT '预占 ID（UUID）',
    account_id VARCHAR(36)  NOT NULL COMMENT '账本账户 ID',
    user_id    VARCHAR(36)  NOT NULL COMMENT '用户 ID（UUID）',
    amount     INT          NOT NULL DEFAULT 1 COMMENT '预占数量',
    status     VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / SETTLED / RELEASED',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    updated_at DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    KEY idx_ebus_credit_hold_account_status (account_id, status),
    KEY idx_ebus_credit_hold_user (user_id),
    CONSTRAINT fk_ebus_credit_hold_account
        FOREIGN KEY (account_id) REFERENCES ebus_credit_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分预占';
