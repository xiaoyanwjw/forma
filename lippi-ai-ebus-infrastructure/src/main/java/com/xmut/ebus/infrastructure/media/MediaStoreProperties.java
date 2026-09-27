package com.xmut.ebus.infrastructure.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ebus.media.*} — store=memory|minio；密钥仅环境变量。
 */
@ConfigurationProperties(prefix = "ebus.media")
public class MediaStoreProperties {

    /** memory（默认）或 minio */
    private String store = "memory";

    private final Minio minio = new Minio();

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Minio getMinio() {
        return minio;
    }

    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "ebus-media";
        private int presignExpirySeconds = 3600;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public int getPresignExpirySeconds() {
            return presignExpirySeconds;
        }

        public void setPresignExpirySeconds(int presignExpirySeconds) {
            this.presignExpirySeconds = presignExpirySeconds;
        }
    }
}
