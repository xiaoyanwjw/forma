package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AgentSession.prompt / resume 出参。
 * 功能描述：返回本轮终态状态、消息与 sessionId。
 */
@Value
@Builder(toBuilder = true)
public class TurnResult {

    String runId;
    String sessionId;
    String finalResponse;

    @Builder.Default
    List<Message> messages = Collections.emptyList();

    @Builder.Default
    Status status = Status.OK;

    TurnResult(String runId, String sessionId, String finalResponse,
               List<Message> messages, Status status) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.finalResponse = finalResponse;
        this.messages = messages == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.status = status != null ? status : Status.OK;
    }

    public enum Status {
        OK,
        NOT_IMPLEMENTED,
        FAILED,
        SUSPENDED,
        CANCELLED
    }

    public static TurnResult failed(String runId, String message) {
        return TurnResult.builder()
                .runId(runId)
                .status(Status.FAILED)
                .finalResponse(message)
                .messages(Collections.emptyList())
                .build();
    }

    public static TurnResult ok(String runId, String sessionId,
                                String finalResponse, List<Message> messages) {
        return TurnResult.builder()
                .runId(runId)
                .sessionId(sessionId)
                .status(Status.OK)
                .finalResponse(finalResponse)
                .messages(messages != null ? messages : Collections.emptyList())
                .build();
    }
}
