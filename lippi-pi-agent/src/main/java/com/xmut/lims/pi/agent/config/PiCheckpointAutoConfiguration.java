package com.xmut.lims.pi.agent.config;

import com.xmut.lims.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.JedisPiRedisCommands;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.PiRedisCommands;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
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
 * 显式开启 Redis Checkpointer 时注册 Redis 实现（{@code @Primary}）。
 *
 * <p>条件：{@code lims.pi.checkpoint.redis.enabled=true} <b>且</b> 存在 {@link JedisPool}。
 * 仅有 {@code JedisPool} 不得抢默认（AD-S2/S9）。未开启时留给 {@link AgentConfiguration}
 * 的 InMemory 回落。Adam 生产目标为 MySQL CP（Story 2.8）。
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
    public Checkpointer piRedisCheckpointer(
            PiRedisCommands redis,
            @Value("${lims.pi.checkpoint.ttl-seconds:7200}") int checkpointTtlSeconds) {
        return new RedisCheckpointer(redis, new CheckpointCodec(), checkpointTtlSeconds);
    }

    @Bean
    @Primary
    public ResumeIdempotencyStore piRedisResumeIdempotencyStore(
            PiRedisCommands redis,
            @Value("${lims.pi.resume-idem.ttl-seconds:86400}") int idemTtlSeconds) {
        return new RedisResumeIdempotencyStore(redis, idemTtlSeconds);
    }
}
