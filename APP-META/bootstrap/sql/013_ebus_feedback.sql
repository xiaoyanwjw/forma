-- Feedback：质量差等简短反馈（不扣生成分；独立于 GenerationRun）
CREATE TABLE IF NOT EXISTS ebus_feedback (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务反馈 ID（UUID）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    artifact_id     VARCHAR(36)  NOT NULL COMMENT '关联成果 biz_id（UUID）',
    tag             VARCHAR(64)  NOT NULL COMMENT '反馈标签，如 质量差',
    comment_text    VARCHAR(512) NULL COMMENT '可选短文',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_feedback_biz (biz_id),
    KEY idx_ebus_feedback_user_time (user_id, created_at),
    KEY idx_ebus_feedback_artifact (artifact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 成果质量反馈';
