package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Value;

import java.util.List;

/**
 * before_model_request 事件载荷。
 * 功能描述：在组 TurnInput / resume 进模型之前发出。
 * 关键设计：只读；prefix 由 extension 返回的 {@link ModelRequestModifier} 决定。
 */
@Value
public class BeforeModelRequestEvent {

    String runId;
    String skillId;
    String workspaceRoot;
    String thisTurnText;
    List<Message> messages;
}
