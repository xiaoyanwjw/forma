-- CreditLedger: 手工改档审计（库内 BIGINT 自增 PK；业务 UUID = biz_id）
CREATE TABLE IF NOT EXISTS ebus_credit_tier_change (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id            VARCHAR(36)  NOT NULL COMMENT '业务审计 ID（UUID）',
    account_id        VARCHAR(36)  NOT NULL COMMENT '账本业务 ID（UUID）',
    target_user_id    VARCHAR(36)  NOT NULL COMMENT '被改档用户业务 ID（UUID）',
    operator_user_id  VARCHAR(36)  NOT NULL COMMENT '操作者用户业务 ID（UUID）',
    from_tier         VARCHAR(16)  NOT NULL COMMENT '改档前套餐档',
    to_tier           VARCHAR(16)  NOT NULL COMMENT '改档后套餐档',
    created_at        DATETIME(3)  NOT NULL COMMENT '改档时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_credit_tier_change_biz (biz_id),
    KEY idx_ebus_credit_tier_change_target (target_user_id),
    KEY idx_ebus_credit_tier_change_operator (operator_user_id),
    KEY idx_ebus_credit_tier_change_account (account_id),
    CONSTRAINT fk_ebus_credit_tier_change_account
        FOREIGN KEY (account_id) REFERENCES ebus_credit_account (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分手工改档审计';
