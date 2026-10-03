package com.xmut.lims.pi.agent.extension;

import lombok.Value;

/**
 * before_agent_start 上下文修改量。
 * 功能描述：对 system prompt 三槽做 overwrite 或 append。
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
