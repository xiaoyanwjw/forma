package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.extension.ContextModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageContextPromptTest {

    @Test
    void page_context_enters_context_not_stable() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SOUL, "SOUL",
                        SystemPromptInput.SKILLS, "SKILLS"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE-CTX"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.BEFORE_AGENT_START, "VOL-EXTRA"))
                .build();
        SystemPromptStable parts = builder.stable(in);
        assertThat(parts.getContext()).contains("PAGE-CTX");
        assertThat(parts.getStable()).doesNotContain("PAGE-CTX");
        assertThat(parts.getStable()).doesNotContain("VOL-EXTRA");
        assertThat(parts.getVariable()).contains("VOL-EXTRA");
        assertThat(parts.getVariable()).doesNotContain("PAGE-CTX");
    }

    @Test
    void before_agent_start_appends_into_matching_segments() {
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.MEMORY, "MEM"))
                .apply(ContextModifier.append("STAB-EXTRA", "CTX-EXTRA", "VAR-EXTRA"))
                .build();
        SystemPromptStable parts = in.parts();
        assertThat(parts.getStable()).contains("SOUL").contains("STAB-EXTRA");
        assertThat(parts.getContext()).contains("PAGE").contains("CTX-EXTRA");
        assertThat(parts.getVariable()).contains("MEM").contains("VAR-EXTRA");
    }
}
