package com.xmut.lims.pi.agent;

import com.xmut.lims.pi.agent.extension.ContextModifier;
import com.xmut.lims.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loop / Graph 的<strong>已绑定 Turn</strong>入参。
 *
 * <p>由 {@link com.xmut.lims.pi.agent.session.AgentSession} hydrate+merge 后产出；
 * {@link #messages} 即进图完整 chat 轴（不含 system）。Loop <strong>不再</strong>追加 user。
 *
 * <p>直测请用 {@link #withUser(String)}，勿再发明「旁路 user 字段」。
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

    TurnInput(String runId,
              List<Message> messages,
              String taskId,
              String sessionId,
              String traceId,
              String skillId,
              String domain,
              String context,
              ContextModifier contextModifier) {
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
