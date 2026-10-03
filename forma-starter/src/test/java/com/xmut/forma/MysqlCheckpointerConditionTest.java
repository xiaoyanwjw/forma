package com.xmut.forma;

import com.xmut.forma.infrastructure.checkpoint.MysqlCheckpointer;
import com.xmut.forma.infrastructure.checkpoint.MysqlCheckpointerConfiguration;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiGraphCheckpointMapper;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.PiRedisCommands;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Story 2.8：MysqlCheckpointer 对 RedisCheckpointer 的 ConditionalOnMissingBean 让位。
 */
class MysqlCheckpointerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MapperStubConfig.class)
            .withConfiguration(AutoConfigurations.of(MysqlCheckpointerConfiguration.class));

    @Test
    void registersMysql_whenRedisAbsent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MysqlCheckpointer.class);
            assertThat(ctx).doesNotHaveBean(RedisCheckpointer.class);
            assertThat(ctx.getBean(Checkpointer.class)).isInstanceOf(MysqlCheckpointer.class);
        });
    }

    @Test
    void skipsMysql_whenRedisPresent() {
        runner.withUserConfiguration(RedisCheckpointPresentConfig.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(MysqlCheckpointer.class);
                    assertThat(ctx).hasSingleBean(RedisCheckpointer.class);
                    assertThat(ctx.getBean(Checkpointer.class)).isInstanceOf(RedisCheckpointer.class);
                });
    }

    @Configuration
    static class MapperStubConfig {
        @Bean
        PiGraphCheckpointMapper piGraphCheckpointMapper() {
            return mock(PiGraphCheckpointMapper.class);
        }
    }

    @Configuration
    static class RedisCheckpointPresentConfig {
        @Bean
        RedisCheckpointer redisCheckpointer() {
            return new RedisCheckpointer(mock(PiRedisCommands.class));
        }
    }
}
