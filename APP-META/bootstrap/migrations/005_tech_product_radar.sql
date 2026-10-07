-- 已有 MySQL：产品雷达亮卡 + 科技速读改名科技前沿。路径在 migrations/，不进 initdb。
UPDATE forma_scene
SET display_name = '科技前沿',
    updated_at = NOW(3)
WHERE scene_code = 'tech_digest';

INSERT INTO forma_scene (biz_id, scene_code, display_name, category, status, sort_order, summary, created_at, updated_at)
SELECT 'a1000001-0001-4000-8000-000000000007',
       'tech_product',
       '产品雷达',
       'tech',
       'AVAILABLE',
       1,
       '贴一个产品官网：分层拆解定位、卖点与公开套餐信号，字段可核对。',
       NOW(3),
       NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM forma_scene WHERE scene_code = 'tech_product');

UPDATE forma_scene
SET display_name = '产品雷达',
    category = 'tech',
    status = 'AVAILABLE',
    sort_order = 1,
    summary = '贴一个产品官网：分层拆解定位、卖点与公开套餐信号，字段可核对。',
    updated_at = NOW(3)
WHERE scene_code = 'tech_product';

UPDATE forma_scene SET sort_order = 2, updated_at = NOW(3) WHERE scene_code = 'tech_digest';
UPDATE forma_scene SET sort_order = 3, updated_at = NOW(3) WHERE scene_code = 'ecommerce';
UPDATE forma_scene SET sort_order = 4, updated_at = NOW(3) WHERE scene_code = 'xiaohongshu';
UPDATE forma_scene SET sort_order = 5, updated_at = NOW(3) WHERE scene_code = 'short_video';
UPDATE forma_scene SET sort_order = 6, updated_at = NOW(3) WHERE scene_code = 'sports_gear';
UPDATE forma_scene SET sort_order = 7, updated_at = NOW(3) WHERE scene_code = 'weekend_trip';
