package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptBuilderTest {

    @Test
    void stable_system_joins_three_tiers() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SOUL, "SOUL",
                        SystemPromptInput.SKILLS, "SKILLS",
                        SystemPromptInput.TOOLS, "TOOLS",
                        SystemPromptInput.CORE, "CORE"))
                .context(SystemPromptInput.mapOf(
                        SystemPromptInput.AGENTS, "AGENTS.md",
                        SystemPromptInput.PI, "HERMES.md"))
                .variable(SystemPromptInput.mapOf(
                        SystemPromptInput.MEMORY, "MEMORY",
                        SystemPromptInput.USER, "USER.md"))
                .build();

        SystemPromptStable parts = builder.stable(in);
        assertThat(parts.getStable())
                .contains("SOUL").contains("SKILLS").contains("TOOLS").contains("CORE");
        assertThat(parts.getStable()).doesNotContain("MEMORY");
        assertThat(parts.getContext()).contains("AGENTS.md").contains("HERMES.md");
        assertThat(parts.getVariable()).contains("MEMORY").contains("USER.md");
        assertThat(parts.getVariable()).doesNotContain("CORE");

        String system = builder.system(in).getContent();
        assertThat(system).isEqualTo(
                parts.getStable() + "\n\n" + parts.getContext() + "\n\n" + parts.getVariable());
        assertThat(system).doesNotContain("hist-only");
    }

    @Test
    void stable_order_soul_skills_tools_core() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        SystemPromptStable parts = builder.stable(SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SOUL, "S1",
                        SystemPromptInput.SKILLS, "S2",
                        SystemPromptInput.TOOLS, "S3",
                        SystemPromptInput.CORE, "S4"))
                .build());
        String stable = parts.getStable();
        assertThat(stable.indexOf("S1")).isLessThan(stable.indexOf("S2"));
        assertThat(stable.indexOf("S2")).isLessThan(stable.indexOf("S3"));
        assertThat(stable.indexOf("S3")).isLessThan(stable.indexOf("S4"));
    }

    @Test
    void stable_hard_cap_truncates_only_stable_segment() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder(32);
        String longSoul = String.join("", Collections.nCopies(20, "ABCDEFGHIJ")); // 200 chars
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, longSoul))
                .context(SystemPromptInput.mapOf(SystemPromptInput.AGENTS, "AGENTS-KEEP"))
                .stableMaxChars(32)
                .build();

        SystemPromptStable parts = builder.stable(in);
        assertThat(parts.getStable()).hasSize(32);
        assertThat(parts.getStable()).endsWith(DefaultPromptBuilder.TRUNCATION_MARKER);
        assertThat(parts.getContext()).isEqualTo("AGENTS-KEEP");

        String system = builder.system(in).getContent();
        assertThat(system).contains("AGENTS-KEEP");
        assertThat(system).contains(DefaultPromptBuilder.TRUNCATION_MARKER);
        String stablePart = system.substring(0, system.indexOf("AGENTS-KEEP")).trim();
        assertThat(stablePart).hasSize(32);
    }

    @Test
    void default_soul_when_blank() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        assertThat(builder.system(SystemPromptInput.builder().build()).getContent())
                .isEqualTo(DefaultPromptBuilder.DEFAULT_SOUL);
    }

    @Test
    void allowlist_ignores_unknown_keys_including_old_contribution() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        java.util.Map<String, String> raw = new java.util.LinkedHashMap<String, String>();
        raw.put(SystemPromptInput.SOUL, "P");
        raw.put("contribution", "FROM-OLD-SPI");
        raw.put("not_a_key", "LEAK");
        String system = builder.system(SystemPromptInput.builder()
                .stable(raw)
                .build()).getContent();
        assertThat(system).contains("P");
        assertThat(system).doesNotContain("FROM-OLD-SPI");
        assertThat(system).doesNotContain("LEAK");
        assertThat(SystemPromptInput.builder().stable(raw).build().getStable())
                .containsOnlyKeys(SystemPromptInput.SOUL);
    }

    @Test
    void sanitize_drops_nulls() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        Message keep = Message.builder().role("user").content("u").build();
        List<Message> out = builder.sanitize(Arrays.asList(null, keep, null));
        assertThat(out).containsExactly(keep);
    }

    @Test
    void silent_dedupe_strips_overlapping_history() {
        List<Message> history = Arrays.asList(
                Message.builder().role("user").content("keep-me").build(),
                Message.builder().role("user").content("this-turn").build(),
                Message.builder().role("tool").content("dup").toolCallId("c1").build(),
                Message.builder().role("user").content("human-reply").build(),
                Message.builder().role("assistant").content("a").build());
        List<Message> out = DefaultPromptBuilder.history(
                history,
                "this-turn",
                "human-reply",
                Collections.singletonList(ToolResult.ok("c1", "echo", "out")));
        assertThat(out).extracting(Message::getContent)
                .containsExactly("keep-me", "a");
    }

    @Test
    void blank_tool_call_id_skipped() {
        List<Message> tools = DefaultPromptBuilder.tools(Arrays.asList(
                ToolResult.ok(null, "echo", "no-id"),
                ToolResult.ok("c1", "echo", "ok")));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getToolCallId()).isEqualTo("c1");
    }

    @Test
    void history_and_chat_never_land_in_system_tiers() {
        DefaultPromptBuilder builder = new DefaultPromptBuilder();
        SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "P"))
                .context(SystemPromptInput.mapOf(SystemPromptInput.AGENTS, "AGENTS"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.MEMORY, "MEMORY"))
                .build();
        assertThat(builder.contextText(in)).isEqualTo("AGENTS");
        assertThat(builder.variableText(in)).isEqualTo("MEMORY");
        assertThat(builder.stableText(in)).isEqualTo("P");
    }
}
