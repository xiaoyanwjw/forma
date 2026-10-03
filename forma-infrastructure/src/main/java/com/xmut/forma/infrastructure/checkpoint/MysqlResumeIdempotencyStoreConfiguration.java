package com.xmut.forma.infrastructure.checkpoint;

import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiResumeIdempotencyMapper;
import com.xmut.lims.pi.agent.config.PiCheckpointAutoConfiguration;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Adam MysqlResumeIdempotencyStore 装配。
 * 功能描述：在 Redis 幂等未注册时提供生产默认 ResumeIdempotencyStore。
 * 关键设计：{@code @AutoConfigureAfter} Redis 装配，避免组件扫描早于 Redis 导致双 Primary。
 */
@AutoConfiguration
@AutoConfigureAfter(PiCheckpointAutoConfiguration.class)
@ConditionalOnMissingBean(RedisResumeIdempotencyStore.class)
public class MysqlResumeIdempotencyStoreConfiguration {

    @Bean
    @Primary
    public MysqlResumeIdempotencyStore mysqlResumeIdempotencyStore(
            PiResumeIdempotencyMapper mapper,
            @Value("${lims.pi.resume-idem.ttl-seconds:86400}") int ttlSeconds) {
        return new MysqlResumeIdempotencyStore(mapper, ttlSeconds);
    }
}
