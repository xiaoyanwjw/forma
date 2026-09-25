package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.extension.ContextModifier;
import com.xmut.lims.pi.agent.extension.PromptSegments;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 可缓存 system prompt 原料。
 * 功能描述：以 Stable / Context / Variable 三个有序 map 组装 system 全文。
 */
public final class SystemPromptInput {

    public static final String SOUL = "soul";
    public static final String SKILLS = "skills";
    public static final String TOOLS = "tools";
    public static final String CORE = "core";
    public static final String AGENTS = "agents";
    public static final String PI = "pi";
    public static final String CONTEXT = "context";
    public static final String MEMORY = "memory";
    public static final String USER = "user";
    public static final String BEFORE_AGENT_START = "before_agent_start";

    /** AD-S10 冻结 allowlist；本阶段禁止新增键。 */
    public static final Set<String> ALLOWED_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SOUL, SKILLS, TOOLS, CORE, AGENTS, PI, CONTEXT, MEMORY, USER, BEFORE_AGENT_START)));

    public static final int DEFAULT_STABLE_MAX_CHARS = 8_000;
    public static final String TRUNCATION_MARKER = "...[truncated]";
    public static final String DEFAULT_SOUL =
            "You are Pi assistant. Follow tenant policy and tool-use rules.";

    private final Map<String, String> stable;
    private final Map<String, String> context;
    private final Map<String, String> variable;
    private final int stableMaxChars;

    /** 非空时 format 忽略对应 map，整段使用该字符串。 */
    private final String stableOverride;
    private final String contextOverride;
    private final String variableOverride;

    private final String stableAppend;
    private final String contextAppend;
    private final String variableAppend;

    SystemPromptInput(Map<String, String> stable,
                      Map<String, String> context,
                      Map<String, String> variable,
                      int stableMaxChars,
                      String stableOverride,
                      String contextOverride,
                      String variableOverride,
                      String stableAppend,
                      String contextAppend,
                      String variableAppend) {
        this.stable = copy(stable);
        this.context = copy(context);
        this.variable = copy(variable);
        this.stableMaxChars = stableMaxChars;
        this.stableOverride = text(stableOverride);
        this.contextOverride = text(contextOverride);
        this.variableOverride = text(variableOverride);
        this.stableAppend = text(stableAppend);
        this.contextAppend = text(contextAppend);
        this.variableAppend = text(variableAppend);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 有序 map；键值成对传入，空白 value 跳过；allowlist 外键忽略。
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
                .stableMaxChars(stableMaxChars)
                .stableOverride(stableOverride)
                .contextOverride(contextOverride)
                .variableOverride(variableOverride)
                .stableAppend(stableAppend)
                .contextAppend(contextAppend)
                .variableAppend(variableAppend);
    }

    /** 先 overwrite 再 append；空白字段忽略。 */
    public SystemPromptInput apply(ContextModifier modifier) {
        return toBuilder().apply(modifier).build();
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
        String base = StringUtils.hasText(stableOverride) ? stableOverride : formatStableFromMaps();
        String withAppend = joinBlocks(base, stableAppend);
        if (!StringUtils.hasText(withAppend)) {
            return "";
        }
        int max = stableMaxChars > 0 ? stableMaxChars : DEFAULT_STABLE_MAX_CHARS;
        return truncate(withAppend, max);
    }

    public String formatContext() {
        String base = StringUtils.hasText(contextOverride) ? contextOverride : joinValues(context);
        return joinBlocks(base, contextAppend);
    }

    public String formatVariable() {
        String base = StringUtils.hasText(variableOverride) ? variableOverride : joinValues(variable);
        return joinBlocks(base, variableAppend);
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

    /**
     * 写入 map；空白 value 删除键；allowlist 外键忽略（AD-S10）。
     */
    static void put(Map<String, String> map, String key, String value) {
        if (!StringUtils.hasText(key) || map == null) {
            return;
        }
        if (!ALLOWED_KEYS.contains(key)) {
            return;
        }
        if (!StringUtils.hasText(value)) {
            map.remove(key);
            return;
        }
        map.put(key, value.trim());
    }

    private String formatStableFromMaps() {
        StringBuilder sb = new StringBuilder();
        String soul = text(stable.get(SOUL));
        block(sb, StringUtils.hasText(soul) ? soul : DEFAULT_SOUL);
        for (Map.Entry<String, String> e : stable.entrySet()) {
            if (SOUL.equals(e.getKey())) {
                continue;
            }
            block(sb, e.getValue());
        }
        return sb.toString().trim();
    }

    private static String joinValues(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (String value : map.values()) {
            block(sb, value);
        }
        return sb.toString().trim();
    }

    private static String joinBlocks(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return StringUtils.hasText(left) ? left.trim() : "";
        }
        if (!StringUtils.hasText(left)) {
            return right.trim();
        }
        return left.trim() + "\n\n" + right.trim();
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
        private String stableOverride;
        private String contextOverride;
        private String variableOverride;
        private String stableAppend;
        private String contextAppend;
        private String variableAppend;

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

        Builder stableOverride(String value) {
            this.stableOverride = value;
            return this;
        }

        Builder contextOverride(String value) {
            this.contextOverride = value;
            return this;
        }

        Builder variableOverride(String value) {
            this.variableOverride = value;
            return this;
        }

        Builder stableAppend(String value) {
            this.stableAppend = value;
            return this;
        }

        Builder contextAppend(String value) {
            this.contextAppend = value;
            return this;
        }

        Builder variableAppend(String value) {
            this.variableAppend = value;
            return this;
        }

        /** 先 overwrite（后写覆盖），再 append（拼接）。 */
        public Builder apply(ContextModifier modifier) {
            if (Objects.isNull(modifier)) {
                return this;
            }

            PromptSegments ow = modifier.getOverwrite();
            if (Objects.nonNull(ow)) {
                if (StringUtils.hasText(ow.getStable())) {
                    this.stableOverride = ow.getStable().trim();
                }
                if (StringUtils.hasText(ow.getContext())) {
                    this.contextOverride = ow.getContext().trim();
                }
                if (StringUtils.hasText(ow.getVariable())) {
                    this.variableOverride = ow.getVariable().trim();
                }
            }
            PromptSegments ap = modifier.getAppend();
            if (Objects.nonNull(ap)) {
                this.stableAppend = joinBlocks(this.stableAppend, ap.getStable());
                this.contextAppend = joinBlocks(this.contextAppend, ap.getContext());
                this.variableAppend = joinBlocks(this.variableAppend, ap.getVariable());
            }
            return this;
        }

        public SystemPromptInput build() {
            return new SystemPromptInput(
                    stable, context, variable, stableMaxChars,
                    stableOverride, contextOverride, variableOverride,
                    stableAppend, contextAppend, variableAppend);
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
    }
}
