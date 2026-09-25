-- Pi resume 幂等占位（库内 BIGINT 自增 PK；业务复合唯一键 run_id + confirm_request_id）
CREATE TABLE IF NOT EXISTS pi_resume_idempotency (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    run_id              VARCHAR(64)  NOT NULL COMMENT '图 runId（UUID 字符串）',
    confirm_request_id  VARCHAR(128) NOT NULL COMMENT '客户端 confirmRequestId',
    phase               VARCHAR(32)  NOT NULL COMMENT 'in_progress | completed',
    result_summary      JSON         NULL COMMENT '完成摘要 JSON（对齐 Redis 形状）',
    updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间',
    expires_at          DATETIME(3)  NOT NULL COMMENT '过期时间（对齐 lims.pi.resume-idem.ttl-seconds）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_resume_idempotency_biz (run_id, confirm_request_id),
    KEY idx_pi_resume_idempotency_expires (expires_at),
    KEY idx_pi_resume_idempotency_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi resume 幂等占位（多 Pod；终态可保留至 TTL）';
