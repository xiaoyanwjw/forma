-- 已有 MySQL：把科技速读从灰卡改为可用。路径在 migrations/，不进 initdb。
-- 全新安装：002 种子已是 AVAILABLE，不必再跑本文件。

UPDATE forma_scene SET status='AVAILABLE', updated_at=NOW(3) WHERE scene_code='tech_digest';
