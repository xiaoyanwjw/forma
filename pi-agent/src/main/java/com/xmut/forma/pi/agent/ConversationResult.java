package com.xmut.forma.pi.agent;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent.run / resume 内部出参。
 * 功能描述：再映射为对外 TurnResult。
 */
@Value
@Builder(toBuilder = true)
public class ConversationResult {

    /** 本次 run 标识；cancel / resume 使用。 */
    String runId;

    /** 终态回复文本（可空）。 */
    String finalResponse;

    /** 本轮结束后的完整 chat 轴（投影，不含 system）。 */
    @Builder.Default
    List<Message> messages = Collections.emptyList();

    /** 运行状态。 */
    @Builder.Default
    Status status = Status.OK;

    ConversationResult(String runId, String finalResponse, List<Message> messages, Status status) {
        this.runId = runId;
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

    public static ConversationResult notImplemented() {
        return ConversationResult.builder()
                .status(Status.NOT_IMPLEMENTED)
                .finalResponse(null)
                .messages(Collections.emptyList())
                .build();
    }

    public static ConversationResult failed(String message) {
        return failed(null, message);
    }

    public static ConversationResult failed(String runId, String message) {
        return ConversationResult.builder()
                .runId(runId)
                .status(Status.FAILED)
                .finalResponse(message)
                .messages(Collections.emptyList())
                .build();
    }

    public static ConversationResult suspended(String message) {
        return suspended(null, message);
    }

    public static ConversationResult suspended(String runId, String message) {
        return ConversationResult.builder()
                .runId(runId)
                .status(Status.SUSPENDED)
                .finalResponse(message)
                .messages(Collections.emptyList())
                .build();
    }

    public static ConversationResult cancelled(String reason) {
        return cancelled(null, reason);
    }

    public static ConversationResult cancelled(String runId, String reason) {
        return ConversationResult.builder()
                .runId(runId)
                .status(Status.CANCELLED)
                .finalResponse(reason)
                .messages(Collections.emptyList())
                .build();
    }

    public static ConversationResult ok(String finalResponse, List<Message> messages) {
        return ok(null, finalResponse, messages);
    }

    public static ConversationResult ok(String runId, String finalResponse, List<Message> messages) {
        return ConversationResult.builder()
                .runId(runId)
                .status(Status.OK)
                .finalResponse(finalResponse)
                .messages(messages != null ? messages : Collections.emptyList())
                .build();
    }
}
