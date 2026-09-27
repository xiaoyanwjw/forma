package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.domain.business.media.repository.MediaObjectRepository;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MediaStoreProperties.class)
public class MediaStoreConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ebus.media", name = "store", havingValue = "memory", matchIfMissing = true)
    public MediaStore inMemoryMediaStore(Clock clock) {
        return new InMemoryMediaStore(clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ebus.media", name = "store", havingValue = "minio")
    public MinioClient minioClient(MediaStoreProperties properties) {
        MediaStoreProperties.Minio minio = properties.getMinio();
        if (!StringUtils.hasText(minio.getAccessKey()) || !StringUtils.hasText(minio.getSecretKey())) {
            throw new IllegalStateException(
                    "ebus.media.store=minio 时必须配置非空的 MINIO_ACCESS_KEY / MINIO_SECRET_KEY（或 ebus.media.minio.access-key / secret-key）");
        }
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw new IllegalStateException("ebus.media.store=minio 时必须配置非空的 MINIO_ENDPOINT（或 ebus.media.minio.endpoint）");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().trim())
                .credentials(minio.getAccessKey().trim(), minio.getSecretKey().trim())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ebus.media", name = "store", havingValue = "minio")
    public MediaStore minioMediaStore(MinioClient minioClient,
                                      MediaObjectRepository mediaObjectRepository,
                                      Clock clock,
                                      MediaStoreProperties properties) {
        MediaStoreProperties.Minio minio = properties.getMinio();
        return new MinioMediaStore(
                minioClient,
                mediaObjectRepository,
                clock,
                minio.getBucket(),
                minio.getPresignExpirySeconds());
    }
}
