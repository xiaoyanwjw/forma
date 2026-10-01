package com.xmut.ebus.application.config;

import com.xmut.lims.pi.agent.config.PiAutoConfiguration;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.ebus.application.business.agent.tool.AskHumanToolHandler;
import com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.BashToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.ReadFileToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.WriteFileToolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Adam's {@link PiToolCatalogConfiguration} wins over pi {@code AgentConfiguration.toolConfig}
 * ({@code @ConditionalOnMissingBean(ToolCatalog)} + ebus {@code @Primary} catalog).
 */
class EbusPrimaryToolCatalogOverrideTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PiToolCatalogConfiguration.class)
            .withConfiguration(AutoConfigurations.of(PiAutoConfiguration.class));

    @Test
    void singleToolCatalogResolvesReadSkillAndSearchSku() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ToolCatalog.class);
            assertThat(context).doesNotHaveBean("toolConfig");

            ToolCatalog catalog = context.getBean(ToolCatalog.class);
            assertThat(catalog.resolve("read_skill")).isPresent();
            assertThat(catalog.resolve("search_sku")).isPresent();
            assertThat(catalog.resolve("ask_human")).isPresent();
            assertThat(catalog.resolve("write_file")).isPresent();
            assertThat(catalog.resolve("read_file")).isPresent();
            assertThat(catalog.resolve("bash")).isPresent();
            assertThat(catalog.handlerOf("read_skill")).isPresent();
            assertThat(catalog.handlerOf("read_skill").get()).isInstanceOf(ReadSkill.class);
            assertThat(catalog.handlerOf("search_sku")).isPresent();
            assertThat(catalog.handlerOf("search_sku").get()).isInstanceOf(SearchSkuToolHandler.class);
            assertThat(catalog.handlerOf("ask_human")).isPresent();
            assertThat(catalog.handlerOf("ask_human").get()).isInstanceOf(AskHumanToolHandler.class);
            assertThat(catalog.handlerOf("write_file").get()).isInstanceOf(WriteFileToolHandler.class);
            assertThat(catalog.handlerOf("read_file").get()).isInstanceOf(ReadFileToolHandler.class);
            assertThat(catalog.handlerOf("bash").get()).isInstanceOf(BashToolHandler.class);
        });
    }
}
