-- SceneCatalog 种子（可重复执行）。与 schema-h2 同序：DELETE 六码 → INSERT。
-- 一级分类（画廊「全部」同序）：tech / ecommerce / content / sports / life
DELETE FROM forma_scene WHERE scene_code IN (
    'ecommerce', 'xiaohongshu', 'short_video', 'tech_product', 'tech_digest', 'sports_gear', 'local_life', 'weekend_trip'
);

INSERT INTO forma_scene (biz_id, scene_code, display_name, category, status, sort_order, summary, created_at, updated_at)
VALUES
    ('a1000001-0001-4000-8000-000000000005', 'tech_digest', '科技速读', 'tech', 'AVAILABLE', 1,
     '丢产品页、AI 文章或技术文档链接：解析正文，一页摘要带走。',
     '2026-10-04 00:00:00.000', '2026-10-04 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000001', 'ecommerce', '电商开店', 'ecommerce', 'AVAILABLE', 2,
     '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000003', 'xiaohongshu', '小红书种草', 'content', 'AVAILABLE', 3,
     '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000002', 'short_video', '短视频带货', 'content', 'COMING_SOON', 4,
     '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
     '2026-09-26 00:00:00.000', '2026-09-26 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000006', 'sports_gear', '装备选购对比', 'sports', 'COMING_SOON', 5,
     '跑鞋、球拍怎么选：对比表 + 一句话推荐，帮你少踩坑。',
     '2026-10-04 00:00:00.000', '2026-10-04 00:00:00.000'),
    ('a1000001-0001-4000-8000-000000000004', 'weekend_trip', '周末行程', 'life', 'COMING_SOON', 6,
     '半天到一天怎么玩：路线、时段和吃饭点，一页带走。',
     '2026-10-04 00:00:00.000', '2026-10-04 00:00:00.000');
