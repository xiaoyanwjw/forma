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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPool;

/**
 * 有 {@link JedisPool} 时注册 Redis Checkpointer / resume 幂等（{@code @Primary}）。
 *
 * <p>无池时本配置不生效，留给 {@link AgentConfiguration} 的 InMemory 回落。
 * CLI 不引入 Jedis，因此仍走内存。
 */
@AutoConfiguration
@ConditionalOnClass(JedisPool.class)
@ConditionalOnBean(JedisPool.class)
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
