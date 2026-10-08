package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.ph.SearchProductLaunchesToolHandler;
import com.xmut.forma.extension.tool.sku.SearchSkuToolHandler;
import com.xmut.forma.extension.tool.tech.ExcerptChunksToolHandler;
import com.xmut.forma.extension.tool.view.RenderViewToolHandler;
import com.xmut.forma.extension.tool.web.FetchWebPageToolHandler;
import com.xmut.forma.extension.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.forma.extension.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.forma.pi.agent.config.PiAutoConfiguration;
import com.xmut.forma.pi.agent.tool.ToolCatalog;
import com.xmut.forma.pi.agent.tool.ToolDefinition;
import com.xmut.forma.pi.agent.tool.base.AskHumanToolHandler;
import com.xmut.forma.pi.agent.tool.base.BashToolHandler;
import com.xmut.forma.pi.agent.tool.base.ReadFileToolHandler;
import com.xmut.forma.pi.agent.tool.base.ReadSkillHandler;
import com.xmut.forma.pi.agent.tool.base.WriteFileToolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ebus must not declare a second {@link ToolCatalog}; the unique bean is
 * pi-agent {@code AgentConfiguration.toolConfig}.
 */
class FormaPrimaryToolCatalogOverrideTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    FormaModelCatalogAutoConfiguration.class,
                    SkuToolsConfiguration.class,
                    XhsToolsConfiguration.class,
                    ViewToolsConfiguration.class,
                    WebFetchToolsConfiguration.class,
                    TechExcerptToolsConfiguration.class,
                    ProductLaunchToolsConfiguration.class)
            .withConfiguration(AutoConfigurations.of(PiAutoConfiguration.class));

    @Test
    void forma_does_not_declare_second_toolCatalog() {
        new ApplicationContextRunner()
                .withUserConfiguration(FormaModelCatalogAutoConfiguration.class)
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
                    .isEqualTo("com.xmut.forma.extension.tool.sku.SearchSkuToolHandler");
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
            assertThat(catalog.resolve("render_view")).isPresent();
            assertThat(catalog.resolve("fetch_web_page")).isPresent();
            assertThat(catalog.resolve("excerpt_chunks")).isPresent();
            assertThat(catalog.resolve("search_product_launches")).isPresent();
            assertThat(catalog.resolve("ask_human")).isPresent();
            assertThat(catalog.resolve("write_file")).isPresent();
            assertThat(catalog.resolve("read_file")).isPresent();
            assertThat(catalog.resolve("bash")).isPresent();
            assertThat(catalog.handlerOf("read_skill")).isPresent();
            assertThat(catalog.handlerOf("read_skill").get()).isInstanceOf(ReadSkillHandler.class);
            assertThat(catalog.handlerOf("search_sku")).isPresent();
            assertThat(catalog.handlerOf("search_sku").get()).isInstanceOf(SearchSkuToolHandler.class);
            assertThat(catalog.handlerOf("search_xhs_note").get()).isInstanceOf(SearchXhsNoteToolHandler.class);
            assertThat(catalog.handlerOf("fetch_xhs_note").get()).isInstanceOf(FetchXhsNoteToolHandler.class);
            assertThat(catalog.handlerOf("render_view").get()).isInstanceOf(RenderViewToolHandler.class);
            assertThat(catalog.handlerOf("fetch_web_page").get()).isInstanceOf(FetchWebPageToolHandler.class);
            assertThat(catalog.handlerOf("excerpt_chunks").get()).isInstanceOf(ExcerptChunksToolHandler.class);
            assertThat(catalog.handlerOf("search_product_launches").get())
                    .isInstanceOf(SearchProductLaunchesToolHandler.class);
            assertThat(catalog.handlerOf("ask_human")).isPresent();
            assertThat(catalog.handlerOf("ask_human").get()).isInstanceOf(AskHumanToolHandler.class);
            assertThat(catalog.handlerOf("write_file").get()).isInstanceOf(WriteFileToolHandler.class);
            assertThat(catalog.handlerOf("read_file").get()).isInstanceOf(ReadFileToolHandler.class);
            assertThat(catalog.handlerOf("bash").get()).isInstanceOf(BashToolHandler.class);
        });
    }
}
