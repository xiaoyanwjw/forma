package com.xmut.forma.pi.ai.config;

import com.xmut.forma.pi.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无 api-key 时须注册可用的 {@link ModelProvider}（Stub 路径），不可为 null bean。
 */
class PiAiAutoConfigurationStubTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PiAiAutoConfiguration.class));

    @Test
    void withoutApiKey_registersUsableModelProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ModelProvider.class);
            ModelProvider provider = context.getBean(ModelProvider.class);
            assertThat(provider).isNotNull();
        });
    }
}
