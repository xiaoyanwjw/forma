package com.xmut.ebus.application.config;

import com.xmut.ebus.extension.config.SkuToolsConfiguration;
import com.xmut.ebus.extension.config.XhsToolsConfiguration;
import com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.lims.pi.agent.config.PiAutoConfiguration;
import com.xmut.lims.pi.agent.tool.AskHumanToolHandler;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.agent.tool.workspace.BashToolHandler;
import com.xmut.lims.pi.agent.tool.workspace.ReadFileToolHandler;
import com.xmut.lims.pi.agent.tool.workspace.WriteFileToolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ebus must not declare a second {@link ToolCatalog}; the unique bean is
 * pi-agent {@code AgentConfiguration.toolConfig}.
 */
class EbusPrimaryToolCatalogOverrideTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    PiToolCatalogConfiguration.class,
                    SkuToolsConfiguration.class,
                    XhsToolsConfiguration.class)
            .withConfiguration(AutoConfigurations.of(PiAutoConfiguration.class));

    @Test
    void ebus_does_not_declare_second_toolCatalog() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(ToolCatalog.class));
    }

    @Test
    void resolves_search_sku_schema_from_json() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ToolCatalog.class);
            assertThat(context).hasBean("toolConfig");

            ToolCatalog catalog = context.getBean(ToolCatalog.class);
            assertThat(catalog.resolve("search_sku")).isPresent();
            ToolDefinition sku = catalog.resolve("search_sku").get();
            assertThat(sku.getSchema()).isNotNull();
            assertThat(sku.getSchema().getParametersSchema()).isNotNull();
            assertThat(sku.getSchema().getParametersSchema().path("properties").has("query")).isTrue();
            assertThat(sku.getHandlerClass())
                    .isEqualTo("com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler");
        });
    }

    @Test
    void singleToolCatalogResolvesReadSkillAndSearchSku() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ToolCatalog.class);
            assertThat(context.getBeansOfType(ToolCatalog.class)).containsOnlyKeys("toolConfig");

            ToolCatalog catalog = context.getBean(ToolCatalog.class);
            assertThat(catalog.resolve("read_skill")).isPresent();
            assertThat(catalog.resolve("search_sku")).isPresent();
            assertThat(catalog.resolve("search_xhs_note")).isPresent();
            assertThat(catalog.resolve("fetch_xhs_note")).isPresent();
            assertThat(catalog.resolve("ask_human")).isPresent();
            assertThat(catalog.resolve("write_file")).isPresent();
            assertThat(catalog.resolve("read_file")).isPresent();
            assertThat(catalog.resolve("bash")).isPresent();
            assertThat(catalog.handlerOf("read_skill")).isPresent();
            assertThat(catalog.handlerOf("read_skill").get()).isInstanceOf(ReadSkill.class);
            assertThat(catalog.handlerOf("search_sku")).isPresent();
            assertThat(catalog.handlerOf("search_sku").get()).isInstanceOf(SearchSkuToolHandler.class);
            assertThat(catalog.handlerOf("search_xhs_note").get()).isInstanceOf(SearchXhsNoteToolHandler.class);
            assertThat(catalog.handlerOf("fetch_xhs_note").get()).isInstanceOf(FetchXhsNoteToolHandler.class);
            assertThat(catalog.handlerOf("ask_human")).isPresent();
            assertThat(catalog.handlerOf("ask_human").get()).isInstanceOf(AskHumanToolHandler.class);
            assertThat(catalog.handlerOf("write_file").get()).isInstanceOf(WriteFileToolHandler.class);
            assertThat(catalog.handlerOf("read_file").get()).isInstanceOf(ReadFileToolHandler.class);
            assertThat(catalog.handlerOf("bash").get()).isInstanceOf(BashToolHandler.class);
        });
    }
}
