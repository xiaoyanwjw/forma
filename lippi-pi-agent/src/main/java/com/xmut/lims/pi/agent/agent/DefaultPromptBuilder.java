package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.ai.message.Message;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * {@link PromptBuilder} 默认实现：委托 {@link SystemPromptInput#format()} + sanitize。
 *
 * <p>SPI 贡献（{@link StableContribution} 等）先写入对应 map 再 format。
 * Context 硬上限与 history 压缩由 {@link ContextCompressor} 负责。
 */
public final class DefaultPromptBuilder implements PromptBuilder {

    /** 默认 Stable 硬上限（字符）。 */
    public static final int DEFAULT_STABLE_MAX_CHARS = SystemPromptInput.DEFAULT_STABLE_MAX_CHARS;

    /** 截断标记（须可测）。 */
    public static final String TRUNCATION_MARKER = SystemPromptInput.TRUNCATION_MARKER;

    /** 缺省 soul（FR31：不得默默丢掉人格/政策段）。 */
    public static final String DEFAULT_SOUL = SystemPromptInput.DEFAULT_SOUL;

    /** @deprecated 使用 {@link #DEFAULT_SOUL} */
    @Deprecated
    public static final String DEFAULT_SKILL_POLICY = DEFAULT_SOUL;

    private final int defaultStableMaxChars;
    private final StableContribution stableContribution;
    private final ContextContribution contextContribution;
    private final VolatileContribution volatileContribution;

    public DefaultPromptBuilder() {
        this(DEFAULT_STABLE_MAX_CHARS, StableContribution.NOOP, ContextContribution.NOOP,
                VolatileContribution.NOOP);
    }

    public DefaultPromptBuilder(int defaultStableMaxChars) {
        this(defaultStableMaxChars, StableContribution.NOOP, ContextContribution.NOOP,
                VolatileContribution.NOOP);
    }

    public DefaultPromptBuilder(int defaultStableMaxChars,
                                StableContribution stableContribution,
                                ContextContribution contextContribution) {
        this(defaultStableMaxChars, stableContribution, contextContribution,
                VolatileContribution.NOOP);
    }

    public DefaultPromptBuilder(int defaultStableMaxChars,
                                StableContribution stableContribution,
                                ContextContribution contextContribution,
                                VolatileContribution volatileContribution) {
        this.defaultStableMaxChars = defaultStableMaxChars > 0
                ? defaultStableMaxChars
                : DEFAULT_STABLE_MAX_CHARS;
        this.stableContribution = stableContribution != null
                ? stableContribution
                : StableContribution.NOOP;
        this.contextContribution = contextContribution != null
                ? contextContribution
                : ContextContribution.NOOP;
        this.volatileContribution = volatileContribution != null
                ? volatileContribution
                : VolatileContribution.NOOP;
    }

    /**
     * 本轮发给模型的列表前缀：有 system 则前置一条 {@code role=system}。
     *
     * <p>不含 sanitize；调用方应 {@code promptBuilder.sanitize(Message.request(...))}。
     */
    public List<Message> format(Message system, List<Message> messages) {
        List<Message> body = messages != null ? messages : Collections.emptyList();
        List<Message> out = new ArrayList<>(body.size() + 1);
        if (Objects.nonNull(system)) {
            out.add(system);
        }
        out.addAll(body);

        return sanitize(out);
    }

    @Override
    public SystemPromptStable stable(SystemPromptInput input) {
        return prepare(input).parts();
    }

    @Override
    public Message system(SystemPromptInput input) {
        return Message.system(prepare(input).format());
    }

    public List<Message> sanitize(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (m != null) {
                out.add(m);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Stable 段文本（已截断）。
     */
    String stableText(SystemPromptInput in) {
        return prepare(in).formatStable();
    }

    String contextText(SystemPromptInput in) {
        return prepare(in).formatContext();
    }

    String variableText(SystemPromptInput in) {
        return prepare(in).formatVariable();
    }

    private SystemPromptInput prepare(SystemPromptInput input) {
        SystemPromptInput in = input != null ? input : SystemPromptInput.builder().build();
        SystemPromptInput.Builder b = in.toBuilder();
        if (in.getStableMaxChars() <= 0) {
            b.stableMaxChars(defaultStableMaxChars);
        }
        if (stableContribution != StableContribution.NOOP
                || contextContribution != ContextContribution.NOOP
                || volatileContribution != VolatileContribution.NOOP) {
            Map<String, String> stable = new LinkedHashMap<String, String>(in.getStable());
            Map<String, String> context = new LinkedHashMap<String, String>(in.getContext());
            Map<String, String> variable = new LinkedHashMap<String, String>(in.getVariable());
            if (stableContribution != StableContribution.NOOP) {
                SystemPromptInput.put(stable, SystemPromptInput.CONTRIBUTION,
                        stableContribution.contribute(in));
            }
            if (contextContribution != ContextContribution.NOOP) {
                SystemPromptInput.put(context, SystemPromptInput.CONTRIBUTION,
                        contextContribution.contribute(in));
            }
            if (volatileContribution != VolatileContribution.NOOP) {
                SystemPromptInput.put(variable, SystemPromptInput.CONTRIBUTION,
                        volatileContribution.contribute(in));
            }
            b.stable(stable).context(context).variable(variable);
        }
        return b.build();
    }

    /**
     * 静默去重：从 history 去掉与本轮 turn 重叠的消息，防双计。
     */
    public static List<Message> history(List<Message> history,
                                        String userMessage,
                                        String humanInput,
                                        List<ToolResult> toolResults) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        String user = textOrNull(userMessage);
        String human = textOrNull(humanInput);
        Set<String> toolCallIds = new HashSet<>();
        if (toolResults != null) {
            for (ToolResult tr : toolResults) {
                if (tr != null && StringUtils.hasText(tr.getCallId())) {
                    toolCallIds.add(tr.getCallId());
                }
            }
        }

        List<Message> out = new ArrayList<>(history.size());
        for (Message m : history) {
            if (m == null) {
                continue;
            }
            String role = m.getRole();
            if ("user".equalsIgnoreCase(role)) {
                String content = trimOrNull(m.getContent());
                if (user != null && user.equals(content)) {
                    continue;
                }
                if (human != null && human.equals(content)) {
                    continue;
                }
            }
            if ("tool".equalsIgnoreCase(role)
                    && StringUtils.hasText(m.getToolCallId())
                    && toolCallIds.contains(m.getToolCallId())) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    /**
     * ToolResult → {@code role=tool} 消息；跳过 null 与 blank {@code callId}。
     */
    public static List<Message> tools(List<ToolResult> toolResults) {
        return Message.tools(toolResults);
    }

    static String resolveSoul(SystemPromptInput in) {
        if (in != null) {
            String soul = in.getStable().get(SystemPromptInput.SOUL);
            if (StringUtils.hasText(soul)) {
                return soul.trim();
            }
        }
        return DEFAULT_SOUL;
    }

    /**
     * Head 保留截断：超限时保留前缀并追加 {@link #TRUNCATION_MARKER}。
     */
    static String truncate(String text, int maxChars) {
        return SystemPromptInput.truncate(text, maxChars);
    }

    private static String textOrNull(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private static String trimOrNull(String raw) {
        return raw == null ? null : raw.trim();
    }
}
