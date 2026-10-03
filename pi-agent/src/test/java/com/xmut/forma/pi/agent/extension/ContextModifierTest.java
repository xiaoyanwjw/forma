package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.agent.agent.SystemPromptInput;
import com.xmut.forma.pi.agent.agent.SystemPromptStable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextModifierTest {

    @Test
    void overwrite_replaces_entire_stable_without_default_soul() {
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SKILLS, "SKILLS"))
                .apply(ContextModifier.overwrite("ONLY-STABLE", null, null))
                .build();

        assertThat(in.formatStable()).isEqualTo("ONLY-STABLE");
        assertThat(in.formatStable()).doesNotContain("SKILLS");
        assertThat(in.formatStable()).doesNotContain(SystemPromptInput.DEFAULT_SOUL);
    }

    @Test
    void append_after_overwrite_on_same_modifier() {
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .apply(ContextModifier.of(
                        PromptSegments.of("OW", null, null),
                        PromptSegments.of("AP", null, null)))
                .build();

        assertThat(in.formatStable()).isEqualTo("OW\n\nAP");
    }

    @Test
    void append_only_keeps_base_maps_then_appends() {
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE"))
                .apply(ContextModifier.append("S-ADD", "C-ADD", "V-ADD"))
                .build();

        SystemPromptStable parts = in.parts();
        assertThat(parts.getStable()).contains("SOUL").contains("S-ADD");
        assertThat(parts.getContext()).contains("PAGE").contains("C-ADD");
        assertThat(parts.getVariable()).isEqualTo("V-ADD");
    }

    @Test
    void blank_fields_are_noop() {
        SystemPromptInput base = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .build();
        SystemPromptInput applied = base.apply(ContextModifier.empty());
        assertThat(applied.formatStable()).isEqualTo(base.formatStable());
    }
}
