-- ArtifactStore: generic billed artifacts (picklist | sku | chat)
CREATE TABLE IF NOT EXISTS ebus_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务成果 ID（UUID = GenerationRun.artifactRef）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    run_id          VARCHAR(36)  NOT NULL COMMENT 'GenerationRun 业务 ID（UUID）',
    artifact_type   VARCHAR(32)  NOT NULL COMMENT 'picklist | sku | chat',
    scene_code      VARCHAR(64)  NOT NULL COMMENT '场景 code，如 ecommerce',
    template_id     VARCHAR(64)  NULL COMMENT '选品模板；sku 可空',
    title           VARCHAR(256) NOT NULL COMMENT '列表摘要',
    payload_json    JSON         NOT NULL COMMENT '类型化载荷',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_artifact_biz (biz_id),
    UNIQUE KEY uk_ebus_artifact_run (run_id),
    KEY idx_ebus_artifact_user_time (user_id, created_at),
    KEY idx_ebus_artifact_user_type (user_id, artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 通用成果 ArtifactStore';
