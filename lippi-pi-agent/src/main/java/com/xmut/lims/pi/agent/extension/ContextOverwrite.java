package com.xmut.lims.pi.agent.extension;

import lombok.Value;

/**
 * {@code before_agent_start} 增量：按段追加进 {@code SystemPromptInput} 的
 * stable / context / variable 三个 map，禁止整段替换 system。
 */
@Value
public class ContextOverwrite {

    String stable;
    String context;
    String variable;

    public static ContextOverwrite empty() {
        return new ContextOverwrite(null, null, null);
    }

    public static ContextOverwrite of(String stable, String context, String variable) {
        return new ContextOverwrite(stable, context, variable);
    }

    public static ContextOverwrite variable(String variable) {
        return of(null, null, variable);
    }
}
