-- SceneCatalog 种子（可重复执行）。与 schema-h2 同序：DELETE 四码 → INSERT。
DELETE FROM forma_scene WHERE scene_code IN ('ecommerce', 'short_video', 'xiaohongshu', 'local_life');

INSERT INTO forma_scene (biz_id, scene_code, display_name, status, sort_order, summary, created_at, updated_at)
VALUES
    ('a1000001-0001-4000-8000-000000000001', 'ecommerce', '电商开店', 'AVAILABLE', 1,
     '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000002', 'short_video', '短视频带货', 'COMING_SOON', 2,
     '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000003', 'xiaohongshu', '小红书种草', 'AVAILABLE', 3,
     '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000004', 'local_life', '本地生活', 'COMING_SOON', 4,
     '到店、团购与周边生意：帮你整理套餐卖点与上架说法。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000');
