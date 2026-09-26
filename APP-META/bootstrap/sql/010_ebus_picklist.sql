-- PicklistArtifact: 选品清单 + 候选条目（库内 BIGINT 自增 PK；业务 UUID = biz_id）
CREATE TABLE IF NOT EXISTS ebus_picklist (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id        VARCHAR(36)  NOT NULL COMMENT '业务清单 ID（UUID）',
    user_id       VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    run_id        VARCHAR(36)  NOT NULL COMMENT 'GenerationRun 业务 ID（UUID）',
    template_id   VARCHAR(64)  NOT NULL COMMENT '品类模板身份（近端固定 domestic-generic-default）',
    disclaimer    VARCHAR(512) NOT NULL COMMENT '非实时数据声明',
    assumptions   VARCHAR(1024) NULL COMMENT '生成时的默认假设说明',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_picklist_biz (biz_id),
    UNIQUE KEY uk_ebus_picklist_run (run_id),
    KEY idx_ebus_picklist_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 选品清单成果';

CREATE TABLE IF NOT EXISTS ebus_picklist_item (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id           VARCHAR(36)  NOT NULL COMMENT '业务条目 ID（UUID）',
    picklist_id      VARCHAR(36)  NOT NULL COMMENT '清单业务 ID（UUID → ebus_picklist.biz_id）',
    sort_order       INT          NOT NULL COMMENT '展示序（0-based）',
    title            VARCHAR(256) NOT NULL COMMENT '品名',
    price_band       VARCHAR(64)  NOT NULL COMMENT '建议客单/价格带',
    reason           VARCHAR(1024) NOT NULL COMMENT '可卖理由',
    differentiation  VARCHAR(512) NOT NULL COMMENT '差异化切入点',
    demand           VARCHAR(512) NOT NULL COMMENT '需求简评',
    competition      VARCHAR(512) NOT NULL COMMENT '竞争简评',
    margin           VARCHAR(512) NOT NULL COMMENT '利润简评',
    risk             VARCHAR(512) NOT NULL COMMENT '风险简评',
    created_at       DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_picklist_item_biz (biz_id),
    KEY idx_ebus_picklist_item_picklist (picklist_id, sort_order),
    CONSTRAINT fk_ebus_picklist_item_picklist
        FOREIGN KEY (picklist_id) REFERENCES ebus_picklist (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 选品清单候选条目';
