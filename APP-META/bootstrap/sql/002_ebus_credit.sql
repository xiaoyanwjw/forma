-- CreditLedger: 账户 + 预占（库内 BIGINT 自增 PK；业务 UUID = biz_id；外键指向业务 UUID）
CREATE TABLE IF NOT EXISTS ebus_credit_account (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id           VARCHAR(36)  NOT NULL COMMENT '业务账本 ID（UUID）',
    user_id          VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID → ebus_user.biz_id）',
    tier             VARCHAR(16)  NOT NULL COMMENT '套餐档：FREE / PRO / PLUS',
    balance          INT          NOT NULL COMMENT '已结算剩余额度（预占占用见 reserved）',
    reserved         INT          NOT NULL DEFAULT 0 COMMENT '活跃预占占用之和',
    period_anchor_at DATETIME(3)  NOT NULL COMMENT '订阅周期锚点',
    next_reset_at    DATETIME(3)  NOT NULL COMMENT '下次月重置时刻',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at       DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_credit_account_biz (biz_id),
    UNIQUE KEY uk_ebus_credit_account_user (user_id),
    KEY idx_ebus_credit_account_next_reset (next_reset_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分账本账户';

CREATE TABLE IF NOT EXISTS ebus_credit_hold (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id     VARCHAR(36)  NOT NULL COMMENT '业务预占 ID（UUID）',
    account_id VARCHAR(36)  NOT NULL COMMENT '账本业务 ID（UUID → ebus_credit_account.biz_id）',
    user_id    VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    amount     INT          NOT NULL DEFAULT 1 COMMENT '预占数量',
    status     VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / SETTLED / RELEASED',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_credit_hold_biz (biz_id),
    KEY idx_ebus_credit_hold_account_status (account_id, status),
    KEY idx_ebus_credit_hold_user (user_id),
    CONSTRAINT fk_ebus_credit_hold_account
        FOREIGN KEY (account_id) REFERENCES ebus_credit_account (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 积分预占';
