package com.xmut.lims.pi.agent.extension;

import lombok.Value;

/**
 * {@code before_agent_start} 增量：按段追加进 {@code SystemPromptInput} 的
 * stable / context / variable 三个 map，禁止整段替换 system。
 */
@Value
public class BeforeAgentStartResult {

    String stable;
    String context;
    String variable;

    public static BeforeAgentStartResult empty() {
        return new BeforeAgentStartResult(null, null, null);
    }

    public static BeforeAgentStartResult of(String stable, String context, String variable) {
        return new BeforeAgentStartResult(stable, context, variable);
    }

    public static BeforeAgentStartResult variable(String variable) {
        return of(null, null, variable);
    }
}
