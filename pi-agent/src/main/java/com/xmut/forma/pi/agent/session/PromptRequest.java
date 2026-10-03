package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AgentSession.prompt 入参。
 * 功能描述：携带本轮增量与会话标识；历史由 SessionStore hydrate。
 */
@Value
@Builder(toBuilder = true)
public class PromptRequest {

    /** 可选客户端指定 runId；空白时由 Runtime 生成。 */
    String runId;

    /** 纯文本用户输入（与 {@link #messages} 二选一或并用）。 */
    String text;

    /** 多模态 / 多轮消息（含 {@code parts}）；OCR 走此字段。 */
    @Builder.Default
    List<Message> messages = Collections.emptyList();

    /** 可空=新建 Session。 */
    String sessionId;

    String traceId;

    /** 显式 Skill id（EXPLICIT），例如 ecommerce-picklist。 */
    String skillId;

    /** 无 skillId 时回退域键。 */
    String domain;

    /** 页面上下文 → Context 段。 */
    String context;

    /** 本 run 工作区绝对路径；可空。 */
    String workspaceRoot;

    PromptRequest(String runId,
                  String text,
                  List<Message> messages,
                  String sessionId,
                  String traceId,
                  String skillId,
                  String domain,
                  String context,
                  String workspaceRoot) {
        this.runId = runId;
        this.text = text;
        this.messages = messages == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.skillId = skillId;
        this.domain = domain;
        this.context = context;
        this.workspaceRoot = workspaceRoot;
    }

}
