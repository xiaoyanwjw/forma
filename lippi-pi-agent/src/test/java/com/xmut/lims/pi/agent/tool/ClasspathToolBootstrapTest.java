package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathToolBootstrapTest {

    @Test
    void load_default_pattern_finds_sample_echo_and_read_skill() {
        List<ToolManifest> manifests = ClasspathToolBootstrap.load();
        assertThat(manifests).extracting(ToolManifest::getId)
                .contains("sample.echo", "read_skill");
        ToolManifest echo = manifests.stream()
                .filter(m -> "sample.echo".equals(m.getId()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(echo.getText()).contains("sample.echo");
        assertThat(echo.getLevel()).isEqualTo(ToolLevel.READ);
        assertThat(echo.getHandlerClass()).isNull();
        assertThat(echo.schemaOrDefault().getName()).isEqualTo("sample.echo");

        ToolManifest readSkill = manifests.stream()
                .filter(m -> "read_skill".equals(m.getId()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(readSkill.getHandlerClass())
                .isEqualTo("com.xmut.lims.pi.agent.tool.handler.ReadSkillHandler");
    }

    @Test
    void merge_overlays_manifest_keeps_handler() {
        List<ToolManifest> scanned = ClasspathToolBootstrap.load();
        ToolHandler handler = (call, ctx) -> null;
        ToolBinding coded = ToolBinding.handlerOnly("sample.echo", handler);

        DefaultToolConfig config = DefaultToolConfig.merge(scanned, Collections.singletonList(coded));

        assertThat(config.resolve("sample.echo")).isPresent();
        assertThat(config.resolve("sample.echo").get().getText()).contains("sample.echo");
        assertThat(config.resolve("sample.echo").get().getLevel()).isEqualTo(ToolLevel.READ);
        assertThat(config.handlerOf("sample.echo")).contains(handler);
        assertThat(config.textForModel()).contains("sample.echo");
        assertThat(config.schemasForModel()).extracting(s -> s.getName()).contains("sample.echo");
    }

    @Test
    void merge_scan_only_exposes_schema_without_handler_for_declaration_tools() {
        List<ToolManifest> scanned = ClasspathToolBootstrap.load();
        // 不经 AutoBinder：sample.echo 无 handler；read_skill 也无（未绑）
        DefaultToolConfig config = DefaultToolConfig.merge(scanned, Collections.emptyList());

        assertThat(config.handlerOf("sample.echo")).isEmpty();
        assertThat(config.schemasForModel()).isNotEmpty();
    }
}
