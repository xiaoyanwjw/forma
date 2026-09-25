-- Pi 图 Checkpoint（HITL 挂起态；≠ Session；一行一 run）
CREATE TABLE IF NOT EXISTS pi_graph_checkpoint (
    run_id          VARCHAR(64)  NOT NULL COMMENT '图 runId（UUID 字符串；PK）',
    checkpoint_id   VARCHAR(64)  NULL COMMENT '最近一次 checkpointId（便于 load）',
    graph_state     JSON         NOT NULL COMMENT 'CheckpointCodec 全量 JSON',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    expires_at      DATETIME(3)  NOT NULL COMMENT '过期时间 UTC（对齐 lims.pi.checkpoint.ttl-seconds）',
    PRIMARY KEY (run_id),
    KEY idx_pi_graph_checkpoint_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 图 Checkpoint（HITL 挂起；终态删除）';
