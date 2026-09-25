-- AgentRuntime: GenerationRun（库内 BIGINT 自增 PK；业务 Run ID = biz_id UUID）
CREATE TABLE IF NOT EXISTS ebus_generation_run (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id       VARCHAR(36)  NOT NULL COMMENT '业务 Run ID（UUID）',
    user_id      VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    hold_id      VARCHAR(36)  NOT NULL COMMENT '积分预占业务 ID（UUID）',
    session_id   VARCHAR(36)  NOT NULL COMMENT 'AgentSession ID（UUID）',
    artifact_ref VARCHAR(36)  NULL COMMENT '成果引用（可空；空跑保持空）',
    status       VARCHAR(16)  NOT NULL COMMENT 'RUNNING / FAILED / SETTLED',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_generation_run_biz (biz_id),
    KEY idx_ebus_generation_run_user (user_id),
    KEY idx_ebus_generation_run_hold (hold_id),
    KEY idx_ebus_generation_run_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 计费生成回合';
