package com.xmut.forma.pi.agent.config;

import com.xmut.forma.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PiRedisCheckpointAutoConfigurationTest {

    @Test
    void withoutJedisPool_fallsBackToInMemory() {
        new ApplicationContextRunner()
                .withUserConfiguration(AgentConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Checkpointer.class);
                    assertThat(context.getBean(Checkpointer.class))
                            .isInstanceOf(InMemoryCheckpointer.class);
                    assertThat(context).hasSingleBean(ResumeIdempotencyStore.class);
                });
    }

    /** 有 JedisPool 但未开 redis.enabled → 不得抢 Primary（AD-S2/S9）。 */
    @Test
    void withJedisPool_withoutRedisEnabled_keepsInMemoryPrimary() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PiCheckpointAutoConfiguration.class))
                .withUserConfiguration(JedisPoolPresentConfig.class, AgentConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(Checkpointer.class))
                            .isInstanceOf(InMemoryCheckpointer.class);
                    assertThat(context).doesNotHaveBean(RedisCheckpointer.class);
                    assertThat(context.getBean(ResumeIdempotencyStore.class))
                            .isInstanceOf(com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore.class);
                    assertThat(context).doesNotHaveBean(RedisResumeIdempotencyStore.class);
                });
    }

    @Test
    void withJedisPool_andRedisEnabled_registersRedisStores() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PiCheckpointAutoConfiguration.class))
                .withUserConfiguration(JedisPoolPresentConfig.class, AgentConfiguration.class)
                .withPropertyValues("lims.pi.checkpoint.redis.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(Checkpointer.class))
                            .isInstanceOf(RedisCheckpointer.class);
                    assertThat(context.getBean(ResumeIdempotencyStore.class))
                            .isInstanceOf(RedisResumeIdempotencyStore.class);
                });
    }

    /** Agent 先注册 InMemory 时，显式 enabled 后 @Primary Redis 仍为 getBean / 注入首选。 */
    @Test
    void withJedisPool_andRedisEnabled_agentConfigFirst_primaryRedisWins() {
        new ApplicationContextRunner()
                .withUserConfiguration(JedisPoolPresentConfig.class, AgentConfiguration.class)
                .withConfiguration(AutoConfigurations.of(PiCheckpointAutoConfiguration.class))
                .withPropertyValues("lims.pi.checkpoint.redis.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(Checkpointer.class))
                            .isInstanceOf(RedisCheckpointer.class);
                    assertThat(context.getBean(ResumeIdempotencyStore.class))
                            .isInstanceOf(RedisResumeIdempotencyStore.class);
                });
    }

    @Configuration
    static class JedisPoolPresentConfig {
        @Bean
        JedisPool jedisPool() {
            return mock(JedisPool.class);
        }
    }
}
