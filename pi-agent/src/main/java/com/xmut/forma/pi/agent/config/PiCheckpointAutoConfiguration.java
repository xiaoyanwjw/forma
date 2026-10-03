package com.xmut.forma.pi.agent.config;

import com.xmut.forma.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.JedisPiRedisCommands;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.PiRedisCommands;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPool;

/**
 * Redis Checkpointer 条件装配。
 * 功能描述：在显式开启且存在 JedisPool 时注册 Redis Checkpointer 为 @Primary。
 * 关键设计：仅有 JedisPool 不得抢默认（AD-S2/S9）。
 */
@AutoConfiguration
@ConditionalOnClass(JedisPool.class)
@ConditionalOnBean(JedisPool.class)
@ConditionalOnProperty(prefix = "lims.pi.checkpoint.redis", name = "enabled", havingValue = "true")
@AutoConfigureBefore(PiAutoConfiguration.class)
public class PiCheckpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PiRedisCommands.class)
    public PiRedisCommands piRedisCommands(JedisPool jedisPool) {
        return new JedisPiRedisCommands(jedisPool);
    }

    @Bean
    @Primary
    public RedisCheckpointer piRedisCheckpointer(
            PiRedisCommands redis,
            @Value("${lims.pi.checkpoint.ttl-seconds:7200}") int checkpointTtlSeconds) {
        return new RedisCheckpointer(redis, new CheckpointCodec(), checkpointTtlSeconds);
    }

    @Bean
    @Primary
    public RedisResumeIdempotencyStore piRedisResumeIdempotencyStore(
            PiRedisCommands redis,
            @Value("${lims.pi.resume-idem.ttl-seconds:86400}") int idemTtlSeconds) {
        return new RedisResumeIdempotencyStore(redis, idemTtlSeconds);
    }
}
