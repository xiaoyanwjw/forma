package com.xmut.forma;

import com.xmut.forma.infrastructure.checkpoint.MysqlResumeIdempotencyStore;
import com.xmut.forma.infrastructure.checkpoint.MysqlResumeIdempotencyStoreConfiguration;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiResumeIdempotencyMapper;
import com.xmut.forma.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.PiRedisCommands;
import com.xmut.forma.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Story 2.8b：MysqlResumeIdempotencyStore 对 RedisResumeIdempotencyStore 的 ConditionalOnMissingBean 让位。
 */
class MysqlResumeIdempotencyStoreConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MapperStubConfig.class)
            .withConfiguration(AutoConfigurations.of(MysqlResumeIdempotencyStoreConfiguration.class));

    @Test
    void registersMysql_whenRedisAbsent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MysqlResumeIdempotencyStore.class);
            assertThat(ctx).doesNotHaveBean(RedisResumeIdempotencyStore.class);
            assertThat(ctx.getBean(ResumeIdempotencyStore.class))
                    .isInstanceOf(MysqlResumeIdempotencyStore.class);
        });
    }

    @Test
    void skipsMysql_whenRedisPresent() {
        runner.withUserConfiguration(RedisResumeIdemPresentConfig.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(MysqlResumeIdempotencyStore.class);
                    assertThat(ctx).hasSingleBean(RedisResumeIdempotencyStore.class);
                    assertThat(ctx.getBean(ResumeIdempotencyStore.class))
                            .isInstanceOf(RedisResumeIdempotencyStore.class);
                });
    }

    @Configuration
    static class MapperStubConfig {
        @Bean
        PiResumeIdempotencyMapper piResumeIdempotencyMapper() {
            return mock(PiResumeIdempotencyMapper.class);
        }
    }

    @Configuration
    static class RedisResumeIdemPresentConfig {
        @Bean
        RedisResumeIdempotencyStore redisResumeIdempotencyStore() {
            return new RedisResumeIdempotencyStore(mock(PiRedisCommands.class));
        }
    }
}
