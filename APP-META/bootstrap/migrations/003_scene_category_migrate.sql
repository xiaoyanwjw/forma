-- 已有 MySQL 卷（建表时尚无 category）用手升：在库上执行本文件一次。
-- 路径故意不在 sql/（initdb）下，避免全新安装重复 ADD COLUMN。
-- 全新 compose initdb：001 已含 category + 002 种子即可；勿对本文件再跑。

ALTER TABLE forma_scene
    ADD COLUMN category VARCHAR(32) NULL COMMENT '一级分类码：tech/ecommerce/content/sports/life'
        AFTER display_name;

UPDATE forma_scene SET category = 'ecommerce' WHERE scene_code = 'ecommerce' AND (category IS NULL OR category = '');
UPDATE forma_scene SET category = 'content' WHERE scene_code IN ('xiaohongshu', 'short_video') AND (category IS NULL OR category = '');
UPDATE forma_scene SET category = 'life' WHERE scene_code IN ('local_life', 'weekend_trip') AND (category IS NULL OR category = '');
UPDATE forma_scene SET category = 'tech' WHERE scene_code IN ('tech_product', 'tech_digest') AND (category IS NULL OR category = '');
UPDATE forma_scene SET category = 'sports' WHERE scene_code = 'sports_gear' AND (category IS NULL OR category = '');
UPDATE forma_scene SET category = 'content' WHERE category IS NULL OR category = '';

ALTER TABLE forma_scene
    MODIFY COLUMN category VARCHAR(32) NOT NULL COMMENT '一级分类码：tech/ecommerce/content/sports/life';

-- 索引：已存在则忽略报错后继续
CREATE INDEX idx_forma_scene_category ON forma_scene (category);

-- 再灌 002_seed_scene.sql（DELETE + INSERT 六码）
