package com.xmut.forma.pi.agent.extension;

import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * System prompt 三段增量。
 * 功能描述：承载 stable / context / variable 的字符串增量。
 */
@Value
public class PromptSegments {

    String stable;
    String context;
    String variable;

    public static PromptSegments empty() {
        return new PromptSegments(null, null, null);
    }

    public static PromptSegments of(String stable, String context, String variable) {
        return new PromptSegments(stable, context, variable);
    }

    public static PromptSegments variable(String variable) {
        return of(null, null, variable);
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(stable)
                && !StringUtils.hasText(context)
                && !StringUtils.hasText(variable);
    }
}
