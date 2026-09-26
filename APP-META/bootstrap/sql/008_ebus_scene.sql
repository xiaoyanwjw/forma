-- SceneCatalog：场景元数据（含灰卡；库内 BIGINT 自增 PK；业务 ID = biz_id UUID）
CREATE TABLE IF NOT EXISTS ebus_scene (
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
    UNIQUE KEY uk_ebus_scene_biz (biz_id),
    UNIQUE KEY uk_ebus_scene_code (scene_code),
    KEY idx_ebus_scene_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 场景目录（SceneCatalog）';

-- 近端四场景种子（固定 biz_id 便于测）；文案对齐 UX mockup index.html
-- 可重复执行：先清近端四码再写入
DELETE FROM ebus_scene WHERE scene_code IN ('ecommerce', 'short_video', 'xiaohongshu', 'local_life');

INSERT INTO ebus_scene (biz_id, scene_code, display_name, status, sort_order, summary, created_at, updated_at)
VALUES
    ('a1000001-0001-4000-8000-000000000001', 'ecommerce', '电商开店', 'AVAILABLE', 1,
     '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000002', 'short_video', '短视频带货', 'COMING_SOON', 2,
     '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000003', 'xiaohongshu', '小红书种草', 'COMING_SOON', 3,
     '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000004', 'local_life', '本地生活', 'COMING_SOON', 4,
     '到店、团购与周边生意：帮你整理套餐卖点与上架说法。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000');
