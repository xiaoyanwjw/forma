package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ContextCompressor 默认实现。
 * 功能描述：对 history 做确定性折叠，并对 Context 段施加硬上限；可选 LLM 摘要。
 * 关键设计：LLM 摘要提示词对齐 Pi CompactionPrompts；失败降级 deterministic，不导致 turn FAILED。
 */
public final class DefaultContextCompressor implements ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextCompressor.class);

    private final CompressionConfig config;
    private final ModelProvider model;

    public DefaultContextCompressor(CompressionConfig config) {
        this(config, null);
    }

    /**
     * @param model 可选；null 时仅做 deterministic 压缩
     */
    public DefaultContextCompressor(CompressionConfig config, ModelProvider model) {
        this.config = Objects.requireNonNull(config, "config");
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
        String hermes = nullToEmpty(input.getContext().get(SystemPromptInput.PI));
        int sep = (!agents.isEmpty() && !hermes.isEmpty()) ? 2 : 0;
        return agents.length() + hermes.length() + sep;
    }

    SystemPromptInput truncateContextIfNeeded(SystemPromptInput input) {
        if (input == null) {
            return null;
        }
        String agents = nullToEmpty(input.getContext().get(SystemPromptInput.AGENTS));
        String hermes = nullToEmpty(input.getContext().get(SystemPromptInput.PI));
        int len = contextChars(input);
        if (len <= config.getContextMaxChars()) {
            return input;
        }
        String joined = agents + (agents.isEmpty() || hermes.isEmpty() ? "" : "\n\n") + hermes;
        String truncated = truncate(joined, config.getContextMaxChars());
        Map<String, String> ctx = new LinkedHashMap<String, String>(input.getContext());
        SystemPromptInput.put(ctx, SystemPromptInput.AGENTS, truncated);
        SystemPromptInput.put(ctx, SystemPromptInput.PI, null);
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
                String message = doSummary(middle, sessionId, runId);
                if (StringUtils.hasText(message)) {
                    return truncate(message.trim(), config.getDigestMaxChars());
                }
            } catch (RuntimeException ex) {
                log.warn("compression LLM failed, falling back to deterministic: {}",
                        ex.toString());
            }
        }
        return deterministicDigest(middle);
    }

    private String doSummary(List<Message> middle, String sessionId, String runId) {
        PreviousSummarySplit split = splitPreviousSummary(middle, config.getSummaryPrefix());
        int maxChars = Math.max(config.getDigestMaxChars() * 8, 8_000);
        String conversationText = serializeForSummarization(split.toSummarize, maxChars);
        String userPrompt = CompactionPrompts.toUserPrompt(conversationText, split.previousSummary);

        List<Message> prompt = new ArrayList<>(2);
        prompt.add(Message.system(CompactionPrompts.SUMMARIZATION_SYSTEM_PROMPT));
        prompt.add(Message.user(userPrompt));
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

    /**
     * 若 middle 含既有 {@code [context_summary]}，拆出 previousSummary 并只摘要其后增量（对齐 Pi UPDATE）。
     */
    static PreviousSummarySplit splitPreviousSummary(List<Message> middle, String summaryPrefix) {
        if (middle == null || middle.isEmpty() || !StringUtils.hasText(summaryPrefix)) {
            return new PreviousSummarySplit(middle, null);
        }
        for (int i = 0; i < middle.size(); i++) {
            Message m = middle.get(i);
            if (m == null || m.getContent() == null) {
                continue;
            }
            if (!m.getContent().startsWith(summaryPrefix)) {
                continue;
            }
            String previous = m.getContent().substring(summaryPrefix.length()).trim();
            List<Message> rest = new ArrayList<>(middle.subList(i + 1, middle.size()));
            if (rest.isEmpty()) {
                // 只有旧摘要、没有新消息：仍把旧摘要当对话正文做首次式重摘要
                return new PreviousSummarySplit(middle, null);
            }
            return new PreviousSummarySplit(rest, previous.isEmpty() ? null : previous);
        }
        return new PreviousSummarySplit(middle, null);
    }

    String deterministicDigest(List<Message> middle) {
        return buildMiddleText(middle, config.getDigestMaxChars());
    }

    /**
     * 对齐 Pi serializeConversation 风格，避免模型把序列化文本当续聊。
     */
    String serializeForSummarization(List<Message> middle, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int per = Math.max(40, config.getDigestPerMessageChars() * 2);
        int max = Math.max(per, maxChars);
        int toolMax = Math.max(200, config.getToolResultMaxChars() > 0
                ? Math.min(config.getToolResultMaxChars() * 2, 2_000) : 2_000);
        for (Message m : middle) {
            if (m == null) {
                continue;
            }
            if (sb.length() >= max) {
                break;
            }
            String role = m.getRole() != null ? m.getRole() : "?";
            String content = nullToEmpty(m.getContent());
            if ("user".equalsIgnoreCase(role)) {
                appendBlock(sb, "[User]: ", truncate(content, per), max);
            } else if ("assistant".equalsIgnoreCase(role)) {
                if (StringUtils.hasText(content)) {
                    appendBlock(sb, "[Assistant]: ", truncate(content, per), max);
                }
                if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    StringBuilder tools = new StringBuilder();
                    boolean first = true;
                    for (ToolCallEntry tc : m.getToolCalls()) {
                        if (tc == null || tc.getToolName() == null) {
                            continue;
                        }
                        if (!first) {
                            tools.append("; ");
                        }
                        tools.append(tc.getToolName()).append("()");
                        first = false;
                    }
                    if (tools.length() > 0) {
                        appendBlock(sb, "[Assistant tool calls]: ", tools.toString(), max);
                    }
                }
            } else if ("tool".equalsIgnoreCase(role)) {
                appendBlock(sb, "[Tool result]: ", truncate(content, toolMax), max);
            } else if ("system".equalsIgnoreCase(role)) {
                // system 不进摘要对话正文
                continue;
            } else {
                appendBlock(sb, "[" + role + "]: ", truncate(content, per), max);
            }
        }
        String out = sb.toString();
        if (out.length() > max) {
            return truncate(out, max);
        }
        return out;
    }

    private static void appendBlock(StringBuilder sb, String prefix, String body, int max) {
        if (sb.length() >= max) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(prefix).append(body);
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

    static final class PreviousSummarySplit {
        final List<Message> toSummarize;
        final String previousSummary;

        PreviousSummarySplit(List<Message> toSummarize, String previousSummary) {
            this.toSummarize = toSummarize == null ? Collections.emptyList() : toSummarize;
            this.previousSummary = previousSummary;
        }
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
