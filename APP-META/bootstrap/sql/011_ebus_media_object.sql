-- MediaStore metadata: bytes live in MinIO/OSS; MySQL has no image BLOB
CREATE TABLE IF NOT EXISTS ebus_media_object (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务 mediaObjectId（UUID）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    object_key      VARCHAR(512) NOT NULL COMMENT '对象存储 key',
    content_type    VARCHAR(128) NOT NULL COMMENT 'MIME，如 image/png',
    size_bytes      BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_media_object_biz (biz_id),
    UNIQUE KEY uk_ebus_media_object_key (object_key),
    KEY idx_ebus_media_object_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 媒体元数据（无图片大字段）';
