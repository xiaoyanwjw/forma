package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.config.AgentConfiguration;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.ai.model.ToolSchema;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCatalogTest {

    @Test
    void unknownTool_isNotRegistered_failClosed() {
        InMemoryToolCatalog policy = InMemoryToolCatalog.empty();
        assertThat(policy.isRegistered("anything")).isFalse();
        assertThat(policy.isRegistered(null)).isFalse();
        assertThat(policy.handlerOf("anything")).isEmpty();
        assertThat(policy.schemasForModel()).isEmpty();
    }

    @Test
    void registeredTools_exposeSchemasAndHandlers() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Arrays.asList(
                ToolTestSupport.tool("read_x", (c, ctx) -> null),
                ToolTestSupport.tool("write_x", (c, ctx) -> null)));

        assertThat(policy.isRegistered("read_x")).isTrue();
        assertThat(policy.isRegistered("write_x")).isTrue();
        assertThat(policy.isRegistered("blocked")).isFalse();
        assertThat(policy.handlerOf("blocked")).isEmpty();
        assertThat(policy.schemasForModel()).extracting(ToolSchema::getName)
                .containsExactly("read_x", "write_x");
        assertThat(policy.handlers()).containsOnlyKeys("read_x", "write_x");
        assertThat(policy.all()).extracting(ToolDefinition::getId)
                .containsExactly("read_x", "write_x");
    }

    @Test
    void textForModel_joins_registered_text() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Arrays.asList(
                ToolTestSupport.tool("a", "Use a for lookups.", (c, ctx) -> null),
                ToolTestSupport.tool("b", "Use b to save.", (c, ctx) -> null),
                ToolTestSupport.tool("d", (c, ctx) -> null)));

        assertThat(policy.textForModel())
                .startsWith("## 工具\n")
                .contains("Use a for lookups.")
                .contains("Use b to save.");
    }

    @Test
    void textForModel_respects_whitelist() {
        InMemoryToolCatalog policy = InMemoryToolCatalog.ofBindings(java.util.Arrays.asList(
                ToolBinding.of(ToolDefinition.builder()
                        .id("a").text("ta")
                        .schema(ToolSchema.builder().name("a").description("a").build()).build(), null),
                ToolBinding.of(ToolDefinition.builder()
                        .id("b").text("tb")
                        .schema(ToolSchema.builder().name("b").description("b").build()).build(), null)));
        assertThat(policy.textForModel(java.util.Collections.singletonList("a"))).isEqualTo("## 工具\nta");
        assertThat(policy.textForModel(java.util.Collections.emptyList())).isNull();
        assertThat(policy.schemasForModel(java.util.Arrays.asList("a", "missing")))
                .extracting(ToolSchema::getName).containsExactly("a");
    }

    @Test
    void textForModel_null_when_no_text() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("a", (c, ctx) -> null)));
        assertThat(policy.textForModel()).isNull();
    }

    @Test
    void duplicateRegistration_rejected() {
        assertThatThrownBy(() -> new InMemoryToolCatalog(Arrays.asList(
                ToolTestSupport.tool("dup", (c, ctx) -> null),
                ToolTestSupport.tool("dup", (c, ctx) -> null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void toolContext_fromNodeContext() {
        com.xmut.forma.pi.agent.graph.NodeContext node =
                new com.xmut.forma.pi.agent.graph.NodeContext("run", "trace");
        ToolContext ctx = ToolContext.from(node);
        assertThat(ctx.getRunId()).isEqualTo("run");
        assertThat(ctx.getTraceId()).isEqualTo("trace");
    }

    @Test
    void emptyRegistrations_ok() {
        assertThat(new InMemoryToolCatalog(Collections.emptyList()).schemasForModel()).isEmpty();
    }

    @Test
    void merge_overlays_manifest_keeps_handler() {
        ToolDefinition echo = ToolDefinition.builder()
                .id("sample.echo")
                .text("[sample.echo] Echo tool guidance")
                .build();
        ToolHandler handler = (call, ctx) -> null;
        ToolBinding coded = ToolBinding.handlerOnly("sample.echo", handler);

        InMemoryToolCatalog config = InMemoryToolCatalog.merge(
                Collections.singletonList(echo), Collections.singletonList(coded));

        assertThat(config.resolve("sample.echo")).isPresent();
        assertThat(config.resolve("sample.echo").get().getText()).contains("sample.echo");
        assertThat(config.handlerOf("sample.echo")).contains(handler);
        assertThat(config.textForModel()).contains("sample.echo");
        assertThat(config.schemasForModel()).extracting(ToolSchema::getName).contains("sample.echo");
    }

    @Test
    void toolCatalog_fails_when_json_has_no_handler_bean() {
        // AutoBinder.handlerOnly 不允许 null Handler；null BeanFactory 跳过 bind，
        // merge 后 scanned id 仍无 Handler，从而走到 requireHandlers fail-fast。
        AgentConfiguration config = new AgentConfiguration();
        assertThatThrownBy(() -> config.toolConfig(
                new InMemorySkillCatalog(),
                new PathMatchingResourcePatternResolver(),
                null))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("tool has no handler");
    }

    @Test
    void requireHandlers_fails_after_merge_when_scanned_id_has_no_handler() {
        ToolDefinition scanned = ToolDefinition.builder()
                .id("orphan_tool")
                .handlerClass("com.example.DemoEchoHandler")
                .schema(ToolSchema.builder().name("orphan_tool").description("orphan").build())
                .build();
        InMemoryToolCatalog catalog = InMemoryToolCatalog.merge(
                Collections.singletonList(scanned), Collections.emptyList());

        assertThatThrownBy(() -> AgentConfiguration.requireHandlers(
                catalog, Collections.singletonList(scanned)))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("tool has no handler")
                .hasMessageContaining("orphan_tool");
    }

    @Test
    void merge_scan_only_exposes_schema_without_handler() {
        ToolDefinition echo = ToolDefinition.builder()
                .id("sample.echo")
                .text("[sample.echo]")
                .build();
        InMemoryToolCatalog config = InMemoryToolCatalog.merge(
                Collections.singletonList(echo), Collections.emptyList());

        assertThat(config.handlerOf("sample.echo")).isEmpty();
        assertThat(config.schemasForModel()).isNotEmpty();
    }
}
