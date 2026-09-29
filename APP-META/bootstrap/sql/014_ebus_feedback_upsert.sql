ALTER TABLE ebus_feedback
    ADD COLUMN updated_at DATETIME(3) NULL COMMENT '更新时间' AFTER created_at;
UPDATE ebus_feedback SET updated_at = created_at WHERE updated_at IS NULL;
DELETE FROM ebus_feedback
 WHERE id NOT IN (
   SELECT keep_id FROM (
     SELECT MAX(id) AS keep_id
       FROM ebus_feedback
      GROUP BY user_id, artifact_id
   ) kept
 );
UPDATE ebus_feedback SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE ebus_feedback
    MODIFY updated_at DATETIME(3) NOT NULL COMMENT '更新时间';
ALTER TABLE ebus_feedback
    ADD UNIQUE KEY uk_ebus_feedback_user_artifact (user_id, artifact_id);
