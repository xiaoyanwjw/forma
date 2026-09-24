package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.ai.model.ToolSchema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolConfigTest {

    @Test
    void unknownTool_isForbidden_failClosed() {
        DefaultToolConfig policy = DefaultToolConfig.empty();
        assertThat(policy.levelOf("anything")).isEqualTo(ToolLevel.FORBIDDEN);
        assertThat(policy.levelOf(null)).isEqualTo(ToolLevel.FORBIDDEN);
        assertThat(policy.handlerOf("anything")).isEmpty();
        assertThat(policy.schemasForModel()).isEmpty();
    }

    @Test
    void registeredLevels_andSchemasExcludeForbidden() {
        DefaultToolConfig policy = new DefaultToolConfig(Arrays.asList(
                ToolTestSupport.registration("read_x", ToolLevel.READ, (c, ctx) -> null),
                ToolTestSupport.registration("write_x", ToolLevel.WRITE, (c, ctx) -> null),
                ToolTestSupport.registration("blocked", ToolLevel.FORBIDDEN, (c, ctx) -> null)));

        assertThat(policy.levelOf("read_x")).isEqualTo(ToolLevel.READ);
        assertThat(policy.levelOf("write_x")).isEqualTo(ToolLevel.WRITE);
        assertThat(policy.levelOf("blocked")).isEqualTo(ToolLevel.FORBIDDEN);
        assertThat(policy.handlerOf("blocked")).isEmpty();
        assertThat(policy.schemasForModel()).extracting(ToolSchema::getName)
                .containsExactly("read_x", "write_x");
        assertThat(policy.handlers()).containsOnlyKeys("read_x", "write_x");
        assertThat(policy.manifests()).extracting(ToolManifest::getId)
                .containsExactly("read_x", "write_x", "blocked");
    }

    @Test
    void textForModel_joins_non_forbidden_text() {
        DefaultToolConfig policy = new DefaultToolConfig(Arrays.asList(
                ToolTestSupport.registration("a", ToolLevel.READ, "Use a for lookups.", (c, ctx) -> null),
                ToolTestSupport.registration("b", ToolLevel.WRITE, "Use b to save.", (c, ctx) -> null),
                ToolTestSupport.registration("c", ToolLevel.FORBIDDEN, "hidden", (c, ctx) -> null),
                ToolTestSupport.registration("d", ToolLevel.READ, (c, ctx) -> null)));

        assertThat(policy.textForModel())
                .contains("Use a for lookups.")
                .contains("Use b to save.")
                .doesNotContain("hidden");
    }

    @Test
    void textForModel_respects_whitelist() {
        DefaultToolConfig policy = DefaultToolConfig.ofBindings(java.util.Arrays.asList(
                ToolBinding.of(ToolManifest.builder()
                        .id("a").text("ta").level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("a").description("a").build()).build(), null),
                ToolBinding.of(ToolManifest.builder()
                        .id("b").text("tb").level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("b").description("b").build()).build(), null)));
        assertThat(policy.textForModel(java.util.Collections.singletonList("a"))).isEqualTo("ta");
        assertThat(policy.textForModel(java.util.Collections.emptyList())).isNull();
        assertThat(policy.schemasForModel(java.util.Arrays.asList("a", "missing")))
                .extracting(ToolSchema::getName).containsExactly("a");
    }

    @Test
    void textForModel_null_when_no_text() {
        DefaultToolConfig policy = new DefaultToolConfig(Collections.singletonList(
                ToolTestSupport.registration("a", ToolLevel.READ, (c, ctx) -> null)));
        assertThat(policy.textForModel()).isNull();
    }

    @Test
    void duplicateRegistration_rejected() {
        assertThatThrownBy(() -> new DefaultToolConfig(Arrays.asList(
                ToolTestSupport.registration("dup", ToolLevel.READ, (c, ctx) -> null),
                ToolTestSupport.registration("dup", ToolLevel.WRITE, (c, ctx) -> null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void toolContext_fromNodeContext() {
        com.xmut.lims.pi.agent.graph.NodeContext node =
                new com.xmut.lims.pi.agent.graph.NodeContext("run", "trace");
        ToolContext ctx = ToolContext.from(node);
        assertThat(ctx.getRunId()).isEqualTo("run");
        assertThat(ctx.getTraceId()).isEqualTo("trace");
    }

    @Test
    void emptyRegistrations_ok() {
        assertThat(new DefaultToolConfig(Collections.emptyList()).schemasForModel()).isEmpty();
    }
}
