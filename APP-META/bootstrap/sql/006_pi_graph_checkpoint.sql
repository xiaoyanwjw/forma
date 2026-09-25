-- Pi 图 Checkpoint（库内 BIGINT 自增 PK；业务键 run_id UNIQUE）
CREATE TABLE IF NOT EXISTS pi_graph_checkpoint (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    run_id          VARCHAR(64)  NOT NULL COMMENT '图 runId（UUID 字符串；业务唯一键）',
    checkpoint_id   VARCHAR(64)  NULL COMMENT '最近一次 checkpointId（便于 load）',
    graph_state     JSON         NOT NULL COMMENT 'CheckpointCodec 全量 JSON',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    expires_at      DATETIME(3)  NOT NULL COMMENT '过期时间（对齐 lims.pi.checkpoint.ttl-seconds）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_graph_checkpoint_run (run_id),
    KEY idx_pi_graph_checkpoint_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 图 Checkpoint（HITL 挂起；终态删除）';
