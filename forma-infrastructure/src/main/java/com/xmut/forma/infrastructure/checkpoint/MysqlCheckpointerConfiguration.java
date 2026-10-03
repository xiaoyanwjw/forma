package com.xmut.forma.infrastructure.checkpoint;

import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiGraphCheckpointMapper;
import com.xmut.forma.pi.agent.config.PiCheckpointAutoConfiguration;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Adam MysqlCheckpointer 装配。
 * 功能描述：在 Redis CP 未注册时提供生产默认 Checkpointer。
 * 关键设计：{@code @AutoConfigureAfter} Redis 装配，避免组件扫描早于 Redis 导致双 Primary。
 */
@AutoConfiguration
@AutoConfigureAfter(PiCheckpointAutoConfiguration.class)
@ConditionalOnMissingBean(RedisCheckpointer.class)
public class MysqlCheckpointerConfiguration {

    @Bean
    @Primary
    public MysqlCheckpointer mysqlCheckpointer(
            PiGraphCheckpointMapper mapper,
            @Value("${lims.pi.checkpoint.ttl-seconds:7200}") int ttlSeconds) {
        return new MysqlCheckpointer(mapper, ttlSeconds);
    }
}
