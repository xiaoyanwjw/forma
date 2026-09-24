package com.xmut.lims.pi.agent.agent;

import lombok.Builder;
import lombok.Value;

/**
 * System prompt 三段文本袋。
 *
 * <p>字段 {@link #variable} 对应上游 key {@code volatile}（避开 Java 保留字）。
 *
 * @see SystemPromptInput#parts()
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
