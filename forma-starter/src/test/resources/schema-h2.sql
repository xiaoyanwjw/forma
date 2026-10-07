-- MySQL SUBSTRING_INDEX is used by session run paging; H2 needs an alias.
CREATE ALIAS IF NOT EXISTS SUBSTRING_INDEX FOR "com.xmut.forma.H2SubstringIndex.substringIndex";

CREATE TABLE IF NOT EXISTS forma_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id        VARCHAR(36)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_user_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_user_username UNIQUE (username),
    CONSTRAINT uk_forma_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS forma_credit_account (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id           VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(36)  NOT NULL,
    tier             VARCHAR(16)  NOT NULL,
    balance          INT          NOT NULL,
    reserved         INT          NOT NULL DEFAULT 0,
    period_anchor_at TIMESTAMP    NOT NULL,
    next_reset_at    TIMESTAMP    NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_credit_account_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_credit_account_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS forma_credit_hold (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id     VARCHAR(36)  NOT NULL,
    account_id VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    amount     INT          NOT NULL DEFAULT 1,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_credit_hold_biz UNIQUE (biz_id)
);

CREATE TABLE IF NOT EXISTS forma_credit_tier_change (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id           VARCHAR(36)  NOT NULL,
    account_id       VARCHAR(36)  NOT NULL,
    target_user_id   VARCHAR(36)  NOT NULL,
    operator_user_id VARCHAR(36)  NOT NULL,
    from_tier        VARCHAR(16)  NOT NULL,
    to_tier          VARCHAR(16)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_credit_tier_change_biz UNIQUE (biz_id)
);

CREATE TABLE IF NOT EXISTS forma_generation_run (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id       VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    hold_id      VARCHAR(36)  NULL,
    exec_hold_id VARCHAR(36)  NULL,
    session_id   VARCHAR(36)  NOT NULL,
    scene_id     VARCHAR(36)  NULL,
    scene_code   VARCHAR(64)  NULL,
    skill_id     VARCHAR(64)  NULL,
    artifact_ref VARCHAR(36)  NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_generation_run_biz UNIQUE (biz_id)
);

CREATE INDEX IF NOT EXISTS idx_forma_generation_run_user ON forma_generation_run (user_id);
CREATE INDEX IF NOT EXISTS idx_forma_generation_run_hold ON forma_generation_run (hold_id);
CREATE INDEX IF NOT EXISTS idx_forma_generation_run_session ON forma_generation_run (session_id);
CREATE INDEX IF NOT EXISTS idx_forma_generation_run_scene ON forma_generation_run (scene_id);
CREATE INDEX IF NOT EXISTS idx_forma_generation_run_scene_code ON forma_generation_run (scene_code);

CREATE TABLE IF NOT EXISTS forma_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id          VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    run_id          VARCHAR(36)  NOT NULL,
    artifact_type   VARCHAR(32)  NOT NULL,
    scene_code      VARCHAR(64)  NOT NULL,
    template_id     VARCHAR(64)  NULL,
    title           VARCHAR(256) NOT NULL,
    payload_json    CLOB         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_artifact_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_artifact_run UNIQUE (run_id)
);
CREATE INDEX IF NOT EXISTS idx_forma_artifact_user_time ON forma_artifact (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_forma_artifact_user_type ON forma_artifact (user_id, artifact_type);

CREATE TABLE IF NOT EXISTS forma_media_object (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id       VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_media_object_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_media_object_key UNIQUE (object_key)
);
CREATE INDEX IF NOT EXISTS idx_forma_media_object_user ON forma_media_object (user_id);

CREATE TABLE IF NOT EXISTS forma_feedback (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id       VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    artifact_id  VARCHAR(36)  NOT NULL,
    tag          VARCHAR(64)  NOT NULL,
    comment_text VARCHAR(512) NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_feedback_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_feedback_user_artifact UNIQUE (user_id, artifact_id)
);
CREATE INDEX IF NOT EXISTS idx_forma_feedback_user_time ON forma_feedback (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_forma_feedback_artifact ON forma_feedback (artifact_id);

CREATE TABLE IF NOT EXISTS pi_session (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id          VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(36)  NULL,
    scene_id            VARCHAR(36)  NULL,
    scene_code          VARCHAR(64)  NULL,
    title               VARCHAR(255) NULL,
    source              VARCHAR(32)  NOT NULL DEFAULT 'api',
    status              VARCHAR(16)  NOT NULL DEFAULT 'active',
    parent_session_id   VARCHAR(64)  NULL,
    compact_anchor_seq  BIGINT       NOT NULL DEFAULT 0,
    last_run_id         VARCHAR(64)  NULL,
    message_count       INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pi_session_session_id UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_pi_session_updated ON pi_session (updated_at);
CREATE INDEX IF NOT EXISTS idx_pi_session_parent ON pi_session (parent_session_id);
CREATE INDEX IF NOT EXISTS idx_pi_session_scene ON pi_session (scene_id);
CREATE INDEX IF NOT EXISTS idx_pi_session_scene_code ON pi_session (scene_code);

CREATE TABLE IF NOT EXISTS pi_session_entry (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id       VARCHAR(36)  NOT NULL,
    session_id   VARCHAR(64)  NOT NULL,
    seq          BIGINT       NOT NULL,
    entry_type   VARCHAR(32)  NOT NULL DEFAULT 'message',
    parent_id    VARCHAR(36)  NULL,
    run_id       VARCHAR(64)  NULL,
    payload      CLOB         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pi_session_entry_biz UNIQUE (biz_id),
    CONSTRAINT uk_pi_session_entry_seq UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_pi_session_entry_run ON pi_session_entry (session_id, run_id);

CREATE TABLE IF NOT EXISTS pi_graph_checkpoint (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id          VARCHAR(64)  NOT NULL,
    checkpoint_id   VARCHAR(64)  NULL,
    graph_state     CLOB         NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pi_graph_checkpoint_run UNIQUE (run_id)
);

CREATE INDEX IF NOT EXISTS idx_pi_graph_checkpoint_expires ON pi_graph_checkpoint (expires_at);

CREATE TABLE IF NOT EXISTS pi_resume_idempotency (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL,
    confirm_request_id  VARCHAR(128) NOT NULL,
    phase               VARCHAR(32)  NOT NULL,
    result_summary      CLOB         NULL,
    updated_at          TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pi_resume_idempotency_biz UNIQUE (run_id, confirm_request_id)
);

CREATE INDEX IF NOT EXISTS idx_pi_resume_idempotency_expires ON pi_resume_idempotency (expires_at);
CREATE INDEX IF NOT EXISTS idx_pi_resume_idempotency_run ON pi_resume_idempotency (run_id);

CREATE TABLE IF NOT EXISTS forma_scene (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id        VARCHAR(36)  NOT NULL,
    scene_code    VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    category      VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    sort_order    INT          NOT NULL,
    summary       VARCHAR(512) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_forma_scene_biz UNIQUE (biz_id),
    CONSTRAINT uk_forma_scene_code UNIQUE (scene_code)
);

CREATE INDEX IF NOT EXISTS idx_forma_scene_sort ON forma_scene (sort_order);
CREATE INDEX IF NOT EXISTS idx_forma_scene_category ON forma_scene (category);

-- 可重复执行：先清近端六码再写入（与 002_seed_scene.sql 同序）
DELETE FROM forma_scene WHERE scene_code IN (
    'ecommerce', 'xiaohongshu', 'short_video', 'tech_product', 'tech_digest', 'sports_gear', 'local_life', 'weekend_trip'
);

INSERT INTO forma_scene (biz_id, scene_code, display_name, category, status, sort_order, summary, created_at, updated_at)
VALUES
    ('a1000001-0001-4000-8000-000000000007', 'tech_product', '产品雷达', 'tech', 'AVAILABLE', 1,
     '贴一个产品官网：分层拆解定位、卖点与公开套餐信号，字段可核对。',
     TIMESTAMP '2026-10-07 00:00:00', TIMESTAMP '2026-10-07 00:00:00'),
    ('a1000001-0001-4000-8000-000000000005', 'tech_digest', '科技前沿', 'tech', 'AVAILABLE', 2,
     '丢产品页、AI 文章或技术文档链接：解析正文，一页摘要带走。',
     TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00'),
    ('a1000001-0001-4000-8000-000000000001', 'ecommerce', '电商开店', 'ecommerce', 'AVAILABLE', 3,
     '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
     TIMESTAMP '2026-09-26 00:00:00', TIMESTAMP '2026-09-26 00:00:00'),
    ('a1000001-0001-4000-8000-000000000003', 'xiaohongshu', '小红书种草', 'content', 'AVAILABLE', 4,
     '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
     TIMESTAMP '2026-09-26 00:00:00', TIMESTAMP '2026-09-26 00:00:00'),
    ('a1000001-0001-4000-8000-000000000002', 'short_video', '短视频带货', 'content', 'COMING_SOON', 5,
     '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
     TIMESTAMP '2026-09-26 00:00:00', TIMESTAMP '2026-09-26 00:00:00'),
    ('a1000001-0001-4000-8000-000000000006', 'sports_gear', '装备选购对比', 'sports', 'COMING_SOON', 6,
     '跑鞋、球拍怎么选：对比表 + 一句话推荐，帮你少踩坑。',
     TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00'),
    ('a1000001-0001-4000-8000-000000000004', 'weekend_trip', '周末行程', 'life', 'COMING_SOON', 7,
     '半天到一天怎么玩：路线、时段和吃饭点，一页带走。',
     TIMESTAMP '2026-10-04 00:00:00', TIMESTAMP '2026-10-04 00:00:00');
