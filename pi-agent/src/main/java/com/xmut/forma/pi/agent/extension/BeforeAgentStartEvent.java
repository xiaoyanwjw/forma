package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.common.output.TurnAttachment;
import lombok.Builder;
import lombok.Value;

/**
 * before_agent_start 事件载荷。
 * 功能描述：在 slash 展开之后、入图之前发出。
 */
@Value
@Builder
public class BeforeAgentStartEvent {

    String runId;
    String skillId;
    String userText;
    /** HITL 选项 id（如 confirm_execute）；prompt 可空。 */
    String resumeOptionId;
    /** 本轮交付附件；闲聊可空（null 视为 empty）。 */
    @Builder.Default
    TurnAttachment attachment = TurnAttachment.empty();

    public BeforeAgentStartEvent(String runId,
                                 String skillId,
                                 String userText,
                                 String resumeOptionId,
                                 TurnAttachment attachment) {
        this.runId = runId;
        this.skillId = skillId;
        this.userText = userText;
        this.resumeOptionId = resumeOptionId;
        this.attachment = attachment == null ? TurnAttachment.empty() : attachment;
    }
}
