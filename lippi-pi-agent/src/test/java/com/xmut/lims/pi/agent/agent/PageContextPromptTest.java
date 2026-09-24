package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.extension.BeforeAgentStartResult;
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
    void loop_input_writes_page_context_and_volatile_extra_into_system_prompt() {
    }

    @Test
    void cache_key_is_run_only() {
        assertThat(SystemPromptCache.cacheKey("r1")).isEqualTo("r1");
        assertThat(SystemPromptCache.cacheKey("r1")).doesNotContain("|");
        assertThat(SystemPromptCache.cacheKey("r1")).doesNotContain("PAGE");
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        SystemPromptCache cache = new SystemPromptCache();
        SystemPromptInput first = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE-A"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.BEFORE_AGENT_START, "VOL-A"))
                .build();
        String cached = cache.getOrBuild(builder, first, "r1").getContent();
        assertThat(cached).contains("PAGE-A").contains("VOL-A");
        SystemPromptInput second = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE-B"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.BEFORE_AGENT_START, "VOL-B"))
                .build();
        String reused = cache.getOrBuild(builder, second, "r1").getContent();
        assertThat(reused).isEqualTo(cached);
        assertThat(reused).doesNotContain("PAGE-B");
        cache.invalidate("r2");
        assertThat(cache.peek().getContent()).isEqualTo(cached);
        cache.invalidate("r1");
        assertThat(cache.peek()).isNull();
    }

    @Test
    void before_agent_start_extends_into_matching_maps() {
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "SOUL"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.CONTEXT, "PAGE"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.MEMORY, "MEM"))
                .extend(BeforeAgentStartResult.of("STAB-EXTRA", "CTX-EXTRA", "VAR-EXTRA"))
                .build();
        SystemPromptStable parts = in.parts();
        assertThat(parts.getStable()).contains("SOUL").contains("STAB-EXTRA");
        assertThat(parts.getContext()).contains("PAGE").contains("CTX-EXTRA");
        assertThat(parts.getVariable()).contains("MEM").contains("VAR-EXTRA");
        assertThat(in.getStable()).containsKey(SystemPromptInput.BEFORE_AGENT_START);
        assertThat(in.getContext()).containsKey(SystemPromptInput.BEFORE_AGENT_START);
        assertThat(in.getVariable()).containsKey(SystemPromptInput.BEFORE_AGENT_START);
    }
}
