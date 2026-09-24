package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.extension.BeforeAgentStartResult;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可缓存 system prompt 原料：只有 Stable / Context / Variable 三个有序 map。
 *
 * <p>{@link #format()} 输出一条 system 全文。{@link BeforeAgentStartResult} 按段
 * {@link #extend(BeforeAgentStartResult) 追加} 到对应 map（键 {@link #BEFORE_AGENT_START}）。
 */
public final class SystemPromptInput {

    public static final String SOUL = "soul";
    public static final String SKILLS = "skills";
    public static final String TOOLS = "tools";
    public static final String CORE = "core";
    public static final String AGENTS = "agents";
    public static final String HERMES = "hermes";
    public static final String CONTEXT = "context";
    public static final String MEMORY = "memory";
    public static final String USER = "user";
    public static final String BEFORE_AGENT_START = "before_agent_start";
    public static final String CONTRIBUTION = "contribution";

    public static final int DEFAULT_STABLE_MAX_CHARS = 8_000;
    public static final String TRUNCATION_MARKER = "...[truncated]";
    public static final String DEFAULT_SOUL =
            "You are AI LIMS Hermes assistant. Follow tenant policy and tool-use rules.";

    private final Map<String, String> stable;
    private final Map<String, String> context;
    private final Map<String, String> variable;
    private final int stableMaxChars;

    SystemPromptInput(Map<String, String> stable,
                      Map<String, String> context,
                      Map<String, String> variable,
                      int stableMaxChars) {
        this.stable = copy(stable);
        this.context = copy(context);
        this.variable = copy(variable);
        this.stableMaxChars = stableMaxChars;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 有序 map；键值成对传入，空白 value 跳过。
     *
     * @throws IllegalArgumentException 参数个数为奇数
     */
    public static Map<String, String> mapOf(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Collections.emptyMap();
        }
        if ((keyValues.length & 1) != 0) {
            throw new IllegalArgumentException("mapOf requires even number of arguments");
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            put(out, keyValues[i], keyValues[i + 1]);
        }
        return out.isEmpty() ? Collections.<String, String>emptyMap() : out;
    }

    public Map<String, String> getStable() {
        return stable;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public Map<String, String> getVariable() {
        return variable;
    }

    public int getStableMaxChars() {
        return stableMaxChars;
    }

    public Builder toBuilder() {
        return new Builder()
                .stable(stable)
                .context(context)
                .variable(variable)
                .stableMaxChars(stableMaxChars);
    }

    /**
     * 把钩子三段增量追加进对应 map；空白忽略。
     */
    public SystemPromptInput extend(BeforeAgentStartResult extra) {
        return toBuilder().extend(extra).build();
    }

    /**
     * {@code join(stable, context, variable)}，跳过空白。
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        block(sb, formatStable());
        block(sb, formatContext());
        block(sb, formatVariable());
        return sb.toString();
    }

    public String formatStable() {
        StringBuilder sb = new StringBuilder();
        String soul = text(stable.get(SOUL));
        block(sb, StringUtils.hasText(soul) ? soul : DEFAULT_SOUL);
        for (Map.Entry<String, String> e : stable.entrySet()) {
            if (SOUL.equals(e.getKey())) {
                continue;
            }
            block(sb, e.getValue());
        }
        String text = sb.toString().trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int max = stableMaxChars > 0 ? stableMaxChars : DEFAULT_STABLE_MAX_CHARS;
        return truncate(text, max);
    }

    public String formatContext() {
        return joinValues(context);
    }

    public String formatVariable() {
        return joinValues(variable);
    }

    public SystemPromptStable parts() {
        return SystemPromptStable.builder()
                .stable(formatStable())
                .context(formatContext())
                .variable(formatVariable())
                .build();
    }

    static String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(0, maxChars);
        }
        return text.substring(0, maxChars - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }

    static void put(Map<String, String> map, String key, String value) {
        if (!StringUtils.hasText(key) || map == null) {
            return;
        }
        if (!StringUtils.hasText(value)) {
            map.remove(key);
            return;
        }
        map.put(key, value.trim());
    }

    private static String joinValues(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (String value : map.values()) {
            block(sb, value);
        }
        return sb.toString().trim();
    }

    private static void block(StringBuilder sb, String block) {
        if (!StringUtils.hasText(block)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(block.trim());
    }

    private static String text(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(src));
    }

    public static final class Builder {
        private final LinkedHashMap<String, String> stable = new LinkedHashMap<String, String>();
        private final LinkedHashMap<String, String> context = new LinkedHashMap<String, String>();
        private final LinkedHashMap<String, String> variable = new LinkedHashMap<String, String>();
        private int stableMaxChars;

        public Builder stable(Map<String, String> map) {
            replace(stable, map);
            return this;
        }

        public Builder context(Map<String, String> map) {
            replace(context, map);
            return this;
        }

        public Builder variable(Map<String, String> map) {
            replace(variable, map);
            return this;
        }

        public Builder stableMaxChars(int stableMaxChars) {
            this.stableMaxChars = stableMaxChars;
            return this;
        }

        public Builder extend(BeforeAgentStartResult extra) {
            if (extra == null) {
                return this;
            }
            joinPut(stable, BEFORE_AGENT_START, extra.getStable());
            joinPut(context, BEFORE_AGENT_START, extra.getContext());
            joinPut(variable, BEFORE_AGENT_START, extra.getVariable());
            return this;
        }

        public SystemPromptInput build() {
            return new SystemPromptInput(stable, context, variable, stableMaxChars);
        }

        private static void replace(LinkedHashMap<String, String> target, Map<String, String> src) {
            target.clear();
            if (src == null) {
                return;
            }
            for (Map.Entry<String, String> e : src.entrySet()) {
                put(target, e.getKey(), e.getValue());
            }
        }

        private static void joinPut(Map<String, String> map, String key, String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            String existing = map.get(key);
            if (!StringUtils.hasText(existing)) {
                map.put(key, value.trim());
                return;
            }
            map.put(key, existing + "\n\n" + value.trim());
        }
    }
}
