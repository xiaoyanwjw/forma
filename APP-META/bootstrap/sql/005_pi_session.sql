-- Pi SessionStore（Message 投影；1 entry = 1 Message；compact 仅锚点）
CREATE TABLE IF NOT EXISTS pi_session (
    session_id          VARCHAR(64)  NOT NULL COMMENT '会话 ID（UUID 字符串）',
    user_id             VARCHAR(36)  NULL COMMENT '可选绑定用户（本阶段 Meta 无 userId，可空）',
    title               VARCHAR(255) NULL COMMENT '会话标题',
    source              VARCHAR(32)  NOT NULL DEFAULT 'api' COMMENT '来源：api / cli / …',
    status              VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT '会话状态',
    parent_session_id   VARCHAR(64)  NULL COMMENT '父会话（listChildren 树；非 compact）',
    compact_anchor_seq  BIGINT       NOT NULL DEFAULT 0 COMMENT '唯一 compact 真相；load 仅 seq > 锚点',
    last_run_id         VARCHAR(64)  NULL COMMENT '最近一轮 runId',
    message_count       INT          NOT NULL DEFAULT 0 COMMENT 'entry 行数（含锚点前）',
    created_at          DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (session_id),
    KEY idx_pi_session_updated (updated_at),
    KEY idx_pi_session_parent (parent_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 会话元数据';

CREATE TABLE IF NOT EXISTS pi_session_entry (
    id           VARCHAR(36)  NOT NULL COMMENT 'Entry UUID',
    session_id   VARCHAR(64)  NOT NULL COMMENT '所属会话',
    seq          BIGINT       NOT NULL COMMENT '单调序号（从 1）',
    entry_type   VARCHAR(32)  NOT NULL DEFAULT 'message' COMMENT '本阶段仅 message',
    parent_id    VARCHAR(36)  NULL COMMENT '消息树边（本阶段恒 null）',
    run_id       VARCHAR(64)  NULL COMMENT 'append / compact run 幂等键',
    payload      JSON         NOT NULL COMMENT '单个 Message JSON 对象',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_session_entry_seq (session_id, seq),
    KEY idx_pi_session_entry_run (session_id, run_id),
    CONSTRAINT fk_pi_session_entry_session
        FOREIGN KEY (session_id) REFERENCES pi_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 会话 Entry（1 行 = 1 Message）';
