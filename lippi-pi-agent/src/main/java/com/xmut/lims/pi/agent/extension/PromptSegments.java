package com.xmut.lims.pi.agent.extension;

import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * System prompt 三段（stable / context / variable）的字符串增量。
 * {@code null} 或空白表示该段不动。
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
