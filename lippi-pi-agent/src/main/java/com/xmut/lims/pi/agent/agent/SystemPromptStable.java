package com.xmut.lims.pi.agent.agent;

import lombok.Builder;
import lombok.Value;

/**
 * System prompt 三段文本袋。
 * 功能描述：存放 stable / context / variable 三段字符串。
 */
@Value
@Builder
public class SystemPromptStable {

    /** soul + skills + tools + core。 */
    String stable;

    /** agents + hermes + pageContext。 */
    String context;

    /** memory + user + before_agent_start（upstream: {@code volatile}）。 */
    String variable;
}
