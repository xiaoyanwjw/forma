-- HITL：GenerationRun 补计费字段（挂起真源仍是 pi_graph_checkpoint）
-- skill_id：resume 重建 SkillRunProfile
-- exec_hold_id：确认执行后的第二笔预占
-- hold_id 可空：= 当前未关闭的活跃预占（策划 settle 后清空）
ALTER TABLE ebus_generation_run
    ADD COLUMN skill_id     VARCHAR(64)  NULL COMMENT 'Skill ID（resume 重建 profile）' AFTER scene_code,
    ADD COLUMN exec_hold_id VARCHAR(36)  NULL COMMENT '执行阶段预占业务 ID（UUID）' AFTER hold_id,
    MODIFY COLUMN hold_id   VARCHAR(36)  NULL COMMENT '当前未关闭的活跃预占（UUID；策划 settle 后可空）';
