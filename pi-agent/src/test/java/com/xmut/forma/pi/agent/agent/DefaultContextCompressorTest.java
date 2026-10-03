package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultContextCompressorTest {

    @Test
    void under_threshold_messages_unchanged() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .protectLastK(8)
                .contextMaxChars(4_000)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("a"));
        messages.add(Message.assistant("b", null));
        messages.add(Message.user("c"));

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemInput(SystemPromptInput.builder()
                        .stable(SystemPromptInput.mapOf(SystemPromptInput.SOUL, "policy"))
                        .build())
                .systemMessage(Message.system("policy"))
                .build());

        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getMessages()).hasSize(3);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("a");
    }

    @Test
    void over_threshold_folds_middle_keeps_tail_k() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(50)
                .protectLastK(2)
                .contextMaxChars(4_000)
                .summaryPrefix("[context_summary]\n")
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            messages.add(Message.user("turn-" + i + "-" + repeat("x", 20)));
        }

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("sys"))
                .build());

        assertThat(result.isCompressed()).isTrue();
        assertThat(result.getMessages()).hasSize(3); // summary + K=2
        assertThat(result.getMessages().get(0).getContent()).startsWith("[context_summary]");
        assertThat(result.getMessages().get(1).getContent()).contains("turn-4");
        assertThat(result.getMessages().get(2).getContent()).contains("turn-5");
    }

    @Test
    void context_over_max_truncated_does_not_touch_stable_fields() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .contextMaxChars(40)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);

        String agents = repeat("A", 80);
        SystemPromptInput input = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SOUL, "SOUL_POLICY",
                        SystemPromptInput.CORE, "CORE_MEMORY"))
                .context(SystemPromptInput.mapOf(
                        SystemPromptInput.AGENTS, agents,
                        SystemPromptInput.PI, "HERMES_MD"))
                .variable(SystemPromptInput.mapOf(
                        SystemPromptInput.MEMORY, "RECALL",
                        SystemPromptInput.USER, "USER_MD"))
                .build();

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(Collections_list(Message.user("hi")))
                .systemInput(input)
                .systemMessage(Message.system("full"))
                .build());

        assertThat(result.isCompressed()).isTrue();
        assertThat(result.isContextChanged()).isTrue();
        assertThat(result.getSystemInput().getStable().get(SystemPromptInput.SOUL)).isEqualTo("SOUL_POLICY");
        assertThat(result.getSystemInput().getStable().get(SystemPromptInput.CORE)).isEqualTo("CORE_MEMORY");
        assertThat(result.getSystemInput().getVariable().get(SystemPromptInput.MEMORY)).isEqualTo("RECALL");
        assertThat(result.getSystemInput().getVariable().get(SystemPromptInput.USER)).isEqualTo("USER_MD");
        String ctx = nullToEmpty(result.getSystemInput().getContext().get(SystemPromptInput.AGENTS))
                + nullToEmpty(result.getSystemInput().getContext().get(SystemPromptInput.PI));
        assertThat(ctx.length()).isLessThanOrEqualTo(40);
        assertThat(ctx).contains(DefaultPromptBuilder.TRUNCATION_MARKER);
    }

    @Test
    void noop_returns_unchanged() {
        List<Message> messages = Collections_list(Message.user("x"));
        CompressionResult result = ContextCompressor.NOOP.compress(CompressionRequest.builder()
                .messages(messages)
                .build());
        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getMessages()).isEqualTo(messages);
    }

    @Test
    void disabled_config_acts_as_noop() {
        DefaultContextCompressor compressor = new DefaultContextCompressor(
                CompressionConfig.builder().enabled(false).maxPromptChars(1).build());
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(Message.user(repeat("z", 100)));
        }
        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());
        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getMessages()).hasSize(20);
    }

    @Test
    void llm_doSummary_failure_falls_back_to_deterministic() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider failing = request -> {
            calls.incrementAndGet();
            assertThat(request.getUseCase()).isEqualTo(ContextCompressor.USE_CASE_COMPRESSION);
            throw new RuntimeException("no catalog");
        };
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(50)
                .protectLastK(2)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config, failing);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(Message.user("m" + i + repeat("y", 30)));
        }

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());

        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.isCompressed()).isTrue();
        assertThat(result.getMessages().get(0).getContent()).startsWith("[context_summary]");
        assertThat(result.getMessages().get(0).getContent()).contains("user:");
    }

    @Test
    void llm_doSummary_uses_pi_compaction_prompts() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider ok = request -> {
            calls.incrementAndGet();
            assertThat(request.getUseCase()).isEqualTo("pi.compression");
            assertThat(request.getUseCase()).isNotEqualTo("pi.default");
            assertThat(request.getMessages()).hasSize(2);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
            assertThat(request.getMessages().get(0).getContent())
                    .isEqualTo(CompactionPrompts.SUMMARIZATION_SYSTEM_PROMPT);
            assertThat(request.getMessages().get(0).getContent())
                    .contains("same primary language");
            String user = request.getMessages().get(1).getContent();
            assertThat(user).contains("<conversation>");
            assertThat(user).contains("</conversation>");
            assertThat(user).contains("## Goal");
            assertThat(user).contains(CompactionPrompts.SUMMARIZATION_PROMPT);
            assertThat(user).contains("When there is NO `<previous-summary>` above");
            assertThat(user).contains("Match the primary language");
            // 指令文案会提到标签名；无 previous 时不应出现成对 previous-summary 块
            assertThat(user).doesNotContain("</previous-summary>");
            assertThat(user).contains("[User]:");
            return ModelResponse.builder().content("LLM_SUMMARY").build();
        };
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(50)
                .protectLastK(1)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config, ok);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            messages.add(Message.user("n" + i + repeat("w", 40)));
        }

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());

        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("[context_summary]\nLLM_SUMMARY");
    }

    @Test
    void llm_doSummary_with_previous_summary_uses_shared_prompt_and_previous_block() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider ok = request -> {
            calls.incrementAndGet();
            String user = request.getMessages().get(1).getContent();
            assertThat(user).contains("<previous-summary>");
            assertThat(user).contains("</previous-summary>");
            assertThat(user).contains("## Goal\nold-goal");
            assertThat(user).contains(CompactionPrompts.SUMMARIZATION_PROMPT);
            assertThat(user).contains("When there IS a `<previous-summary>` above");
            assertThat(user).contains("[User]:");
            assertThat(user).contains("new-turn");
            return ModelResponse.builder().content("UPDATED_SUMMARY").build();
        };
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(50)
                .protectLastK(1)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config, ok);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("[context_summary]\n## Goal\nold-goal"));
        messages.add(Message.user("new-turn " + repeat("x", 40)));
        messages.add(Message.user("tail-keep " + repeat("y", 40)));
        messages.add(Message.user("tail-last"));

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());

        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.getMessages().get(0).getContent())
                .isEqualTo("[context_summary]\nUPDATED_SUMMARY");
    }

    @Test
    void splitPreviousSummary_extracts_prefix_message() {
        List<Message> middle = new ArrayList<>();
        middle.add(Message.user("[context_summary]\n## Goal\nkeep"));
        middle.add(Message.user("delta"));
        DefaultContextCompressor.PreviousSummarySplit split =
                DefaultContextCompressor.splitPreviousSummary(middle, "[context_summary]\n");
        assertThat(split.previousSummary).isEqualTo("## Goal\nkeep");
        assertThat(split.toSummarize).hasSize(1);
        assertThat(split.toSummarize.get(0).getContent()).isEqualTo("delta");
    }

    @Test
    void approx_tokens_is_chars_div_4() {
        assertThat(DefaultContextCompressor.approxTokens(400)).isEqualTo(100);
    }

    @Test
    void under_threshold_long_tool_outside_tail_unchanged() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .protectLastK(2)
                .toolResultMaxChars(20)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);

        String longTool = repeat("T", 200);
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("u0"));
        messages.add(Message.assistant("call", null));
        messages.add(Message.tool("c1", longTool));
        messages.add(Message.user("u1"));
        messages.add(Message.user("u2"));

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("sys"))
                .build());

        assertThat(result.isCompressed()).isFalse();
        assertThat(result.getMessages().get(2).getContent()).isEqualTo(longTool);
    }

    @Test
    void truncate_head_respects_max_when_shorter_than_marker() {
        assertThat(DefaultContextCompressor.approxTokens(4)).isEqualTo(1);
        // via public compress path: contextMaxChars=5 < marker length
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .contextMaxChars(5)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);
        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(Collections_list(Message.user("hi")))
                .systemInput(SystemPromptInput.builder()
                        .context(SystemPromptInput.mapOf(SystemPromptInput.AGENTS, repeat("A", 50)))
                        .build())
                .systemMessage(Message.system("s"))
                .build());
        assertThat(result.isContextChanged()).isTrue();
        assertThat(result.getSystemInput().getContext().get(SystemPromptInput.AGENTS).length())
                .isLessThanOrEqualTo(5);
    }

    @Test
    void context_gate_counts_separator_between_agents_and_hermes() {
        // agents=3 + sep=2 + hermes=3 = 8 > contextMaxChars=7 → must truncate
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .contextMaxChars(7)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);
        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(Collections_list(Message.user("hi")))
                .systemInput(SystemPromptInput.builder()
                        .context(SystemPromptInput.mapOf(
                                SystemPromptInput.AGENTS, "ABC",
                                SystemPromptInput.PI, "XYZ"))
                        .build())
                .systemMessage(Message.system("s"))
                .build());
        assertThat(result.isContextChanged()).isTrue();
        int ctx = DefaultContextCompressor.contextChars(result.getSystemInput());
        assertThat(ctx).isLessThanOrEqualTo(7);
    }

    @Test
    void history_cut_keeps_assistant_with_tool_results_in_tail() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(30)
                .protectLastK(1)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("old-" + repeat("x", 40)));
        messages.add(Message.user("mid-" + repeat("y", 40)));
        messages.add(Message.assistant("call",
                java.util.Collections.singletonList(
                        new com.xmut.forma.pi.ai.tool.ToolCallEntry("id1", "search", null))));
        messages.add(Message.tool("id1", "tool-result-body"));

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());

        assertThat(result.isCompressed()).isTrue();
        List<Message> out = result.getMessages();
        // 无对齐时 cut 会落在 tool 上；对齐后 tail 应保留 assistant+tool
        assertThat(out.get(out.size() - 1).getRole()).isEqualToIgnoringCase("tool");
        assertThat(out.get(out.size() - 2).getRole()).isEqualToIgnoringCase("assistant");
        assertThat(out.get(out.size() - 2).getToolCalls()).isNotEmpty();
    }

    @Test
    void llm_doSummary_output_capped_to_digest_max() {
        ModelProvider verbose = request -> ModelResponse.builder()
                .content(repeat("L", 500))
                .build();
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(50)
                .protectLastK(1)
                .digestMaxChars(40)
                .build();
        DefaultContextCompressor compressor = new DefaultContextCompressor(config, verbose);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            messages.add(Message.user("n" + i + repeat("w", 40)));
        }

        CompressionResult result = compressor.compress(CompressionRequest.builder()
                .messages(messages)
                .systemMessage(Message.system("s"))
                .build());

        String summary = result.getMessages().get(0).getContent();
        assertThat(summary).startsWith("[context_summary]");
        assertThat(summary.length() - "[context_summary]\n".length()).isLessThanOrEqualTo(40);
    }

    private static List<Message> Collections_list(Message... msgs) {
        List<Message> list = new ArrayList<>();
        for (Message m : msgs) {
            list.add(m);
        }
        return list;
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static String nullToEmpty(String raw) {
        return raw != null ? raw : "";
    }
}
