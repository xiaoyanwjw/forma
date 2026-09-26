-- AD-15：计费 GenerationRun 与 pi_session 必绑场景（可空列兼容开发期空表；应用层强制）
ALTER TABLE ebus_generation_run
    ADD COLUMN scene_id   VARCHAR(36)  NULL COMMENT '场景业务 ID（UUID）' AFTER session_id,
    ADD COLUMN scene_code VARCHAR(64)  NULL COMMENT '稳定场景码' AFTER scene_id,
    ADD KEY idx_ebus_generation_run_scene (scene_id),
    ADD KEY idx_ebus_generation_run_scene_code (scene_code);

ALTER TABLE pi_session
    ADD COLUMN scene_id   VARCHAR(36)  NULL COMMENT '场景业务 ID（UUID）' AFTER user_id,
    ADD COLUMN scene_code VARCHAR(64)  NULL COMMENT '稳定场景码' AFTER scene_id,
    ADD KEY idx_pi_session_scene (scene_id),
    ADD KEY idx_pi_session_scene_code (scene_code);
