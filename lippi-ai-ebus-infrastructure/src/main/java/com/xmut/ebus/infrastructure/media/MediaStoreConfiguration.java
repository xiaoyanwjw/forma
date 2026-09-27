package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.domain.business.media.repository.MediaObjectRepository;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.Proxy;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

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
        // IDEA / 系统 HTTP 代理常把 localhost MinIO 打成 502 Non-XML；对象存储直连、不走 proxy
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().trim())
                .credentials(minio.getAccessKey().trim(), minio.getSecretKey().trim())
                .httpClient(httpClient)
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
