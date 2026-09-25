package com.xmut.lims.pi.agent.extension;

import lombok.Value;

/**
 * {@code before_agent_start} 对 system prompt 三槽的修改：
 * {@link #overwrite} 整段替换；{@link #append} 接到该段末尾。
 *
 * <p>应用到 {@code SystemPromptInput} 时固定顺序：先 overwrite，再 append。
 */
@Value
public class ContextModifier {

    PromptSegments overwrite;
    PromptSegments append;

    public static ContextModifier empty() {
        return new ContextModifier(null, null);
    }

    public static ContextModifier of(PromptSegments overwrite, PromptSegments append) {
        return new ContextModifier(overwrite, append);
    }

    public static ContextModifier overwrite(String stable, String context, String variable) {
        return of(PromptSegments.of(stable, context, variable), null);
    }

    public static ContextModifier append(String stable, String context, String variable) {
        return of(null, PromptSegments.of(stable, context, variable));
    }

    /** 仅追加 variable（扩展常用）。 */
    public static ContextModifier appendVariable(String variable) {
        return append(null, null, variable);
    }
}
