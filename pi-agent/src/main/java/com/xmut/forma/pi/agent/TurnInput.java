package com.xmut.forma.pi.agent;

import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 已绑定的一轮图入参。
 * 功能描述：携带完整 chat 轴（不含 system）及 run/session 标识。
 * 关键设计：由 AgentSession hydrate+merge 后产出；Agent 不再追加 user。
 */
@Value
@Builder(toBuilder = true)
public class TurnInput {

    String runId;

    /** 进图完整 chat 轴（不含 system）。 */
    @Builder.Default
    List<Message> messages = Collections.emptyList();

    String taskId;

    String sessionId;

    String traceId;

    String skillId;

    String domain;

    /** 页面上下文 → {@code SystemPromptInput.context}。 */
    String context;

    /** {@code before_agent_start} 对三槽的 overwrite / append。 */
    ContextModifier contextModifier;

    /** 本 run 工作区绝对路径；可空。 */
    String workspaceRoot;

    TurnInput(String runId,
              List<Message> messages,
              String taskId,
              String sessionId,
              String traceId,
              String skillId,
              String domain,
              String context,
              ContextModifier contextModifier,
              String workspaceRoot) {
        this.runId = runId;
        this.messages = messages == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.skillId = skillId;
        this.domain = domain;
        this.context = context;
        this.contextModifier = contextModifier;
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Loop / 单测工厂：单条 user 构成完整 chat 轴。
     *
     * <pre>{@code
     * TurnInput.withUser("hi").build()
     * }</pre>
     */
    public static TurnInputBuilder withUser(String text) {
        Objects.requireNonNull(text, "text");
        return builder().messages(Collections.singletonList(Message.user(text)));
    }

    public void requireNonNullRequest() {
        Objects.requireNonNull(this, "TurnInput");
    }
}
