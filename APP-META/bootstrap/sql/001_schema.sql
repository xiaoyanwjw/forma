-- Forma schema (final). Identity / credits / scene / Pi session / generation / artifact / media / feedback.
-- Compose initdb runs this directory alphabetically on a *new* volume only.
-- Existing volumes: recreate (`docker compose down -v`) or apply equivalent DDL by hand.

-- Identity
CREATE TABLE IF NOT EXISTS forma_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id        VARCHAR(36)  NOT NULL COMMENT '业务/对外用户 ID（UUID）',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    email         VARCHAR(255) NOT NULL COMMENT '邮箱',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_user_biz (biz_id),
    UNIQUE KEY uk_forma_user_username (username),
    UNIQUE KEY uk_forma_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 用户账号';

-- CreditLedger
CREATE TABLE IF NOT EXISTS forma_credit_account (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id           VARCHAR(36)  NOT NULL COMMENT '业务账本 ID（UUID）',
    user_id          VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID → forma_user.biz_id）',
    tier             VARCHAR(16)  NOT NULL COMMENT '套餐档：FREE / PRO / PLUS',
    balance          INT          NOT NULL COMMENT '已结算剩余额度（预占占用见 reserved）',
    reserved         INT          NOT NULL DEFAULT 0 COMMENT '活跃预占占用之和',
    period_anchor_at DATETIME(3)  NOT NULL COMMENT '订阅周期锚点',
    next_reset_at    DATETIME(3)  NOT NULL COMMENT '下次月重置时刻',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at       DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_credit_account_biz (biz_id),
    UNIQUE KEY uk_forma_credit_account_user (user_id),
    KEY idx_forma_credit_account_next_reset (next_reset_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 积分账本账户';

CREATE TABLE IF NOT EXISTS forma_credit_hold (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id     VARCHAR(36)  NOT NULL COMMENT '业务预占 ID（UUID）',
    account_id VARCHAR(36)  NOT NULL COMMENT '账本业务 ID（UUID → forma_credit_account.biz_id）',
    user_id    VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    amount     INT          NOT NULL DEFAULT 1 COMMENT '预占数量',
    status     VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / SETTLED / RELEASED',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_credit_hold_biz (biz_id),
    KEY idx_forma_credit_hold_account_status (account_id, status),
    KEY idx_forma_credit_hold_user (user_id),
    CONSTRAINT fk_forma_credit_hold_account
        FOREIGN KEY (account_id) REFERENCES forma_credit_account (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 积分预占';

CREATE TABLE IF NOT EXISTS forma_credit_tier_change (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id            VARCHAR(36)  NOT NULL COMMENT '业务审计 ID（UUID）',
    account_id        VARCHAR(36)  NOT NULL COMMENT '账本业务 ID（UUID）',
    target_user_id    VARCHAR(36)  NOT NULL COMMENT '被改档用户业务 ID（UUID）',
    operator_user_id  VARCHAR(36)  NOT NULL COMMENT '操作者用户业务 ID（UUID）',
    from_tier         VARCHAR(16)  NOT NULL COMMENT '改档前套餐档',
    to_tier           VARCHAR(16)  NOT NULL COMMENT '改档后套餐档',
    created_at        DATETIME(3)  NOT NULL COMMENT '改档时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_credit_tier_change_biz (biz_id),
    KEY idx_forma_credit_tier_change_target (target_user_id),
    KEY idx_forma_credit_tier_change_operator (operator_user_id),
    KEY idx_forma_credit_tier_change_account (account_id),
    CONSTRAINT fk_forma_credit_tier_change_account
        FOREIGN KEY (account_id) REFERENCES forma_credit_account (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 积分手工改档审计';

-- SceneCatalog
CREATE TABLE IF NOT EXISTS forma_scene (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id        VARCHAR(36)  NOT NULL COMMENT '业务场景 ID（UUID）',
    scene_code    VARCHAR(64)  NOT NULL COMMENT '稳定场景码（绑定能力包）',
    display_name  VARCHAR(64)  NOT NULL COMMENT '画廊展示名',
    status        VARCHAR(16)  NOT NULL COMMENT 'AVAILABLE / COMING_SOON',
    sort_order    INT          NOT NULL COMMENT '画廊排序（升序）',
    summary       VARCHAR(512) NOT NULL COMMENT '卡片短文案',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_scene_biz (biz_id),
    UNIQUE KEY uk_forma_scene_code (scene_code),
    KEY idx_forma_scene_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 场景目录（SceneCatalog）';

-- Pi SessionStore
CREATE TABLE IF NOT EXISTS pi_session (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    session_id          VARCHAR(64)  NOT NULL COMMENT '会话业务 ID（UUID 字符串）',
    user_id             VARCHAR(36)  NULL COMMENT '绑定用户业务 ID（可空）',
    scene_id            VARCHAR(36)  NULL COMMENT '场景业务 ID（UUID）',
    scene_code          VARCHAR(64)  NULL COMMENT '稳定场景码',
    title               VARCHAR(255) NULL COMMENT '会话标题',
    source              VARCHAR(32)  NOT NULL DEFAULT 'api' COMMENT '来源：api / cli / …',
    status              VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT '会话状态',
    parent_session_id   VARCHAR(64)  NULL COMMENT '父会话',
    compact_anchor_seq  BIGINT       NOT NULL DEFAULT 0 COMMENT 'compact 锚点；load 仅 seq > 锚点',
    last_run_id         VARCHAR(64)  NULL COMMENT '最近一轮 runId',
    message_count       INT          NOT NULL DEFAULT 0 COMMENT 'entry 行数（含锚点前）',
    created_at          DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_session_session_id (session_id),
    KEY idx_pi_session_updated (updated_at),
    KEY idx_pi_session_parent (parent_session_id),
    KEY idx_pi_session_scene (scene_id),
    KEY idx_pi_session_scene_code (scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 会话元数据';

CREATE TABLE IF NOT EXISTS pi_session_entry (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id       VARCHAR(36)  NOT NULL COMMENT 'Entry 业务 ID（UUID）',
    session_id   VARCHAR(64)  NOT NULL COMMENT '所属会话业务 ID',
    seq          BIGINT       NOT NULL COMMENT '单调序号（从 1）',
    entry_type   VARCHAR(32)  NOT NULL DEFAULT 'message' COMMENT '本阶段仅 message',
    parent_id    VARCHAR(36)  NULL COMMENT '消息树边',
    run_id       VARCHAR(64)  NULL COMMENT 'append / compact run 幂等键',
    payload      JSON         NOT NULL COMMENT '单个 Message JSON 对象',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_session_entry_biz (biz_id),
    UNIQUE KEY uk_pi_session_entry_seq (session_id, seq),
    KEY idx_pi_session_entry_run (session_id, run_id),
    CONSTRAINT fk_pi_session_entry_session
        FOREIGN KEY (session_id) REFERENCES pi_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 会话 Entry（1 行 = 1 Message）';

CREATE TABLE IF NOT EXISTS pi_graph_checkpoint (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    run_id          VARCHAR(64)  NOT NULL COMMENT '图 runId（UUID 字符串；业务唯一键）',
    checkpoint_id   VARCHAR(64)  NULL COMMENT '最近一次 checkpointId',
    graph_state     JSON         NOT NULL COMMENT 'CheckpointCodec 全量 JSON',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    expires_at      DATETIME(3)  NOT NULL COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_graph_checkpoint_run (run_id),
    KEY idx_pi_graph_checkpoint_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi 图 Checkpoint（HITL 挂起；终态删除）';

CREATE TABLE IF NOT EXISTS pi_resume_idempotency (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    run_id              VARCHAR(64)  NOT NULL COMMENT '图 runId（UUID 字符串）',
    confirm_request_id  VARCHAR(128) NOT NULL COMMENT '客户端 confirmRequestId',
    phase               VARCHAR(32)  NOT NULL COMMENT 'in_progress | completed',
    result_summary      JSON         NULL COMMENT '完成摘要 JSON',
    updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间',
    expires_at          DATETIME(3)  NOT NULL COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_resume_idempotency_biz (run_id, confirm_request_id),
    KEY idx_pi_resume_idempotency_expires (expires_at),
    KEY idx_pi_resume_idempotency_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pi resume 幂等占位';

-- AgentRuntime GenerationRun（含场景绑定 + HITL 字段）
CREATE TABLE IF NOT EXISTS forma_generation_run (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id       VARCHAR(36)  NOT NULL COMMENT '业务 Run ID（UUID）',
    user_id      VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    hold_id      VARCHAR(36)  NULL COMMENT '当前未关闭的活跃预占（UUID；策划 settle 后可空）',
    exec_hold_id VARCHAR(36)  NULL COMMENT '执行阶段预占业务 ID（UUID）',
    session_id   VARCHAR(36)  NOT NULL COMMENT 'AgentSession ID（UUID）',
    scene_id     VARCHAR(36)  NULL COMMENT '场景业务 ID（UUID）',
    scene_code   VARCHAR(64)  NULL COMMENT '稳定场景码',
    skill_id     VARCHAR(64)  NULL COMMENT 'Skill ID（resume 重建 profile）',
    artifact_ref VARCHAR(36)  NULL COMMENT '成果引用（可空；空跑保持空）',
    status       VARCHAR(16)  NOT NULL COMMENT 'RUNNING / FAILED / SETTLED',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_generation_run_biz (biz_id),
    KEY idx_forma_generation_run_user (user_id),
    KEY idx_forma_generation_run_hold (hold_id),
    KEY idx_forma_generation_run_session (session_id),
    KEY idx_forma_generation_run_scene (scene_id),
    KEY idx_forma_generation_run_scene_code (scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 计费生成回合';

CREATE TABLE IF NOT EXISTS forma_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务成果 ID（UUID = GenerationRun.artifactRef）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    run_id          VARCHAR(36)  NOT NULL COMMENT 'GenerationRun 业务 ID（UUID）',
    artifact_type   VARCHAR(32)  NOT NULL COMMENT 'picklist | sku | chat | xhs_*',
    scene_code      VARCHAR(64)  NOT NULL COMMENT '场景 code，如 ecommerce',
    template_id     VARCHAR(64)  NULL COMMENT '选品模板；sku 可空',
    title           VARCHAR(256) NOT NULL COMMENT '列表摘要',
    payload_json    JSON         NOT NULL COMMENT '类型化载荷（含 view）',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_artifact_biz (biz_id),
    UNIQUE KEY uk_forma_artifact_run (run_id),
    KEY idx_forma_artifact_user_time (user_id, created_at),
    KEY idx_forma_artifact_user_type (user_id, artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 通用成果 ArtifactStore';

CREATE TABLE IF NOT EXISTS forma_media_object (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务 mediaObjectId（UUID）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    object_key      VARCHAR(512) NOT NULL COMMENT '对象存储 key',
    content_type    VARCHAR(128) NOT NULL COMMENT 'MIME，如 image/png',
    size_bytes      BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_media_object_biz (biz_id),
    UNIQUE KEY uk_forma_media_object_key (object_key),
    KEY idx_forma_media_object_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 媒体元数据（无图片大字段）';

CREATE TABLE IF NOT EXISTS forma_feedback (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务反馈 ID（UUID）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    artifact_id     VARCHAR(36)  NOT NULL COMMENT '关联成果 biz_id（UUID）',
    tag             VARCHAR(64)  NOT NULL COMMENT '反馈标签，如 质量差',
    comment_text    VARCHAR(512) NULL COMMENT '可选短文',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forma_feedback_biz (biz_id),
    UNIQUE KEY uk_forma_feedback_user_artifact (user_id, artifact_id),
    KEY idx_forma_feedback_user_time (user_id, created_at),
    KEY idx_forma_feedback_artifact (artifact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Forma 成果质量反馈';
