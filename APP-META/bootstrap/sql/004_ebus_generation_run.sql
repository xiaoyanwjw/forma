-- AgentRuntime: GenerationRun（hold + session + artifact 关联；UUID PK；时间戳 UTC）
CREATE TABLE IF NOT EXISTS ebus_generation_run (
    id           VARCHAR(36)  NOT NULL COMMENT 'Run ID（UUID）',
    user_id      VARCHAR(36)  NOT NULL COMMENT '用户 ID（UUID）',
    hold_id      VARCHAR(36)  NOT NULL COMMENT '积分预占 ID（UUID）',
    session_id   VARCHAR(36)  NOT NULL COMMENT 'AgentSession ID（UUID）',
    artifact_ref VARCHAR(36)  NULL COMMENT '成果引用（可空；空跑保持空）',
    status       VARCHAR(16)  NOT NULL COMMENT 'RUNNING / FAILED / SETTLED',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    KEY idx_ebus_generation_run_user (user_id),
    KEY idx_ebus_generation_run_hold (hold_id),
    KEY idx_ebus_generation_run_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 计费生成回合';
