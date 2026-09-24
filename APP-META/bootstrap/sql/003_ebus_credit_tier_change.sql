-- CreditLedger: 手工改档审计（谁 / 何时 / 从→到 / 目标用户；UTC）
CREATE TABLE IF NOT EXISTS ebus_credit_tier_change (
    id                VARCHAR(36)  NOT NULL COMMENT '审计 ID（UUID）',
    account_id        VARCHAR(36)  NOT NULL COMMENT '账本账户 ID',
    target_user_id    VARCHAR(36)  NOT NULL COMMENT '被改档用户 ID（UUID）',
    operator_user_id  VARCHAR(36)  NOT NULL COMMENT '操作者用户 ID（UUID）',
    from_tier         VARCHAR(16)  NOT NULL COMMENT '改档前套餐档',
    to_tier           VARCHAR(16)  NOT NULL COMMENT '改档后套餐档',
    created_at        DATETIME(3)  NOT NULL COMMENT '改档时刻 UTC',
    PRIMARY KEY (id),
    KEY idx_ebus_credit_tier_change_target (target_user_id),
    KEY idx_ebus_credit_tier_change_operator (operator_user_id),
    KEY idx_ebus_credit_tier_change_account (account_id),
    CONSTRAINT fk_ebus_credit_tier_change_account
        FOREIGN KEY (account_id) REFERENCES ebus_credit_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分手工改档审计';
