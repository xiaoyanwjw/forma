package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认可注入 {@link ContextCompressor}：deterministic history 折叠 + Context 段硬上限。
 *
 * <p>可选 {@link ModelProvider}：{@link ContextCompressor#USE_CASE_COMPRESSION} 摘要；
 * Catalog 未注册 / 调用失败 → deterministic。字符估 token：{@code chars / 4}。
 */
public final class DefaultContextCompressor implements ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextCompressor.class);

    private final CompressionConfig config;
    private final ModelProvider model;

    public DefaultContextCompressor() {
        this(CompressionConfig.defaults(), null);
    }

    public DefaultContextCompressor(CompressionConfig config) {
        this(config, null);
    }

    public DefaultContextCompressor(CompressionConfig config, ModelProvider model) {
        this.config = config != null ? config : CompressionConfig.defaults();
        this.model = model;
    }

    @Override
    public CompressionResult compress(CompressionRequest request) {
        if (request == null || !config.isEnabled()) {
            return CompressionResult.unchanged(request);
        }

        List<Message> messages = new ArrayList<>(request.getMessages());
        SystemPromptInput input = request.getSystemInput();
        boolean contextChanged = false;
        boolean compressed = false;

        // Context 硬上限（始终可执行；不改 Stable 字段）
        int oldContextChars = contextChars(input);
        SystemPromptInput compressedInput = truncateContextIfNeeded(input);
        if (compressedInput != input && compressedInput != null) {
            input = compressedInput;
            contextChanged = true;
            compressed = true;
        }
        int newContextChars = contextChars(input);

        // 阈值：用截断后的 Context 长度校正 system 估值（避免误触发 history 折叠）
        int totalChars = estimateChars(request.getSystemMessage(), messages);
        if (contextChanged && request.getSystemMessage() != null) {
            totalChars = totalChars - oldContextChars + newContextChars;
        }

        // Phase0 + history 折叠：仅超阈时改 messages（AC6 未超阈 messages 不变）
        if (totalChars >= config.getMaxPromptChars()) {
            List<Message> pruned = pruneLongToolResultsOutsideTail(messages);
            if (pruned != messages) {
                messages = pruned;
                compressed = true;
            }

            List<Message> folded = doCompress(messages, request.getSessionId(), request.getRunId());
            if (folded != messages) {
                messages = folded;
                compressed = true;
            }
        }

        if (!compressed) {
            return CompressionResult.unchanged(request);
        }

        return CompressionResult.builder()
                .messages(Collections.unmodifiableList(messages))
                .systemInput(input)
                .contextChanged(contextChanged)
                .compressed(true)
                .build();
    }

    /**
     * 字符 rough estimate；{@code approxTokens = chars / 4}。
     */
    public static int approxTokens(int chars) {
        return chars / 4;
    }

    static int estimateChars(Message system, List<Message> messages) {
        int n = 0;
        if (system != null && system.getContent() != null) {
            n += system.getContent().length();
        }
        if (messages != null) {
            for (Message m : messages) {
                if (m != null && m.getContent() != null) {
                    n += m.getContent().length();
                }
            }
        }
        return n;
    }

    /**
     * agents + hermes（含拼接分隔符）字符数，与 {@link #truncateContextIfNeeded} 门禁一致。
     */
    static int contextChars(SystemPromptInput input) {
        if (input == null) {
            return 0;
        }
        String agents = nullToEmpty(input.getContext().get(SystemPromptInput.AGENTS));
        String hermes = nullToEmpty(input.getContext().get(SystemPromptInput.HERMES));
        int sep = (!agents.isEmpty() && !hermes.isEmpty()) ? 2 : 0;
        return agents.length() + hermes.length() + sep;
    }

    SystemPromptInput truncateContextIfNeeded(SystemPromptInput input) {
        if (input == null) {
            return null;
        }
        String agents = nullToEmpty(input.getContext().get(SystemPromptInput.AGENTS));
        String hermes = nullToEmpty(input.getContext().get(SystemPromptInput.HERMES));
        int len = contextChars(input);
        if (len <= config.getContextMaxChars()) {
            return input;
        }
        String joined = agents + (agents.isEmpty() || hermes.isEmpty() ? "" : "\n\n") + hermes;
        String truncated = truncate(joined, config.getContextMaxChars());
        Map<String, String> ctx = new LinkedHashMap<String, String>(input.getContext());
        SystemPromptInput.put(ctx, SystemPromptInput.AGENTS, truncated);
        SystemPromptInput.put(ctx, SystemPromptInput.HERMES, null);
        return input.toBuilder().context(ctx).build();
    }

    List<Message> doCompress(List<Message> messages, String sessionId, String runId) {
        int k = config.effectiveProtectLastK();
        if (messages == null || messages.size() <= k + 1) {
            return messages;
        }
        int cut = alignCutToToolPairs(messages, messages.size() - k);
        if (cut <= 0 || cut >= messages.size()) {
            return messages;
        }
        List<Message> middle = new ArrayList<>(messages.subList(0, cut));
        List<Message> tail = new ArrayList<>(messages.subList(cut, messages.size()));
        if (middle.isEmpty()) {
            return messages;
        }
        String digest = summary(middle, sessionId, runId);
        Message summary = Message.user(config.getSummaryPrefix() + digest);
        List<Message> out = new ArrayList<>(tail.size() + 1);
        out.add(summary);
        out.addAll(tail);
        return out;
    }

    /**
     * 避免把 assistant(tool_calls) 与后续 tool 结果切开：若尾部以孤立 tool 开头，
     * 将 cut 前移以把对应 assistant 一并纳入 tail。
     */
    static int alignCutToToolPairs(List<Message> messages, int cut) {
        if (messages == null || cut <= 0 || cut >= messages.size()) {
            return cut;
        }
        int aligned = cut;
        while (aligned > 0 && aligned < messages.size() && isToolRole(messages.get(aligned))) {
            aligned--;
        }
        return aligned;
    }

    private static boolean isToolRole(Message m) {
        return m != null && m.getRole() != null && "tool".equalsIgnoreCase(m.getRole());
    }

    private String summary(List<Message> middle, String sessionId, String runId) {
        if (model != null) {
            try {
                String llm = doSummary(middle, sessionId, runId);
                if (llm != null && !llm.trim().isEmpty()) {
                    return truncate(llm.trim(), config.getDigestMaxChars());
                }
            } catch (RuntimeException ex) {
                log.warn("hermes compression LLM failed, falling back to deterministic: {}",
                        ex.toString());
            }
        }
        return deterministicDigest(middle);
    }

    private String doSummary(List<Message> middle, String sessionId, String runId) {
        // 给模型看比 digest 更完整的 middle 文本（仍有硬上限），输出再截到 digestMaxChars
        int llmInputMax = Math.max(config.getDigestMaxChars() * 4, config.getDigestMaxChars());
        String body = buildMiddleText(middle, llmInputMax);
        List<Message> prompt = new ArrayList<>(2);
        prompt.add(Message.system(
                "Summarize the following conversation middle turns briefly for context continuity."));
        prompt.add(Message.user(body));
        ModelResponse response = model.complete(ModelRequest.builder()
                .messages(prompt)
                .useCase(USE_CASE_COMPRESSION)
                .sessionId(sessionId)
                .runId(runId)
                .build());
        if (response == null || response.getContent() == null) {
            return null;
        }
        return response.getContent();
    }

    String deterministicDigest(List<Message> middle) {
        return buildMiddleText(middle, config.getDigestMaxChars());
    }

    private String buildMiddleText(List<Message> middle, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int per = Math.max(20, config.getDigestPerMessageChars());
        int max = Math.max(per, maxChars);
        for (Message m : middle) {
            if (m == null) {
                continue;
            }
            if (sb.length() >= max) {
                break;
            }
            String role = m.getRole() != null ? m.getRole() : "?";
            String content = truncate(nullToEmpty(m.getContent()), per);
            sb.append(role).append(": ").append(content);
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                sb.append(" tools=[");
                boolean first = true;
                for (ToolCallEntry tc : m.getToolCalls()) {
                    if (tc == null || tc.getToolName() == null) {
                        continue;
                    }
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(tc.getToolName());
                    first = false;
                }
                sb.append(']');
            }
            sb.append('\n');
        }
        String out = sb.toString();
        if (out.length() > max) {
            return truncate(out, max);
        }
        return out;
    }

    List<Message> pruneLongToolResultsOutsideTail(List<Message> messages) {
        int maxTool = config.getToolResultMaxChars();
        if (maxTool <= 0 || messages == null || messages.isEmpty()) {
            return messages;
        }
        int k = config.effectiveProtectLastK();
        int cut = alignCutToToolPairs(messages, Math.max(0, messages.size() - k));
        boolean changed = false;
        List<Message> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i < cut
                    && m != null
                    && "tool".equalsIgnoreCase(m.getRole())
                    && m.getContent() != null
                    && m.getContent().length() > maxTool) {
                out.add(m.toBuilder()
                        .content("[tool_result_truncated] len=" + m.getContent().length())
                        .build());
                changed = true;
            } else {
                out.add(m);
            }
        }
        return changed ? out : messages;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        String marker = DefaultPromptBuilder.TRUNCATION_MARKER;
        if (maxChars <= marker.length()) {
            return marker.substring(0, maxChars);
        }
        int keep = maxChars - marker.length();
        return text.substring(0, keep) + marker;
    }

    private static String nullToEmpty(String raw) {
        return raw != null ? raw : "";
    }
}
