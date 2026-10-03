package com.xmut.lims.pi.ai.model;

import com.xmut.lims.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 模型补全请求 <b>[Lippi]</b>。
 *
 * <p><strong>禁止</strong>使用 agent 风格 {@code ChatRequest} 作 hermes 公共 API。
 * {@code tools} 可选：null / empty → 不带 tools；非空 → native 或按 Catalog 文本回落。
 */
@Value
@Builder(toBuilder = true)
public class ModelRequest {

    /** 有序消息（system / user / assistant / tool）。 */
    List<Message> messages;

    /** 可选工具 schema；null 或 empty 表示本轮不带 tools。 */
    List<ToolSchema> tools;

    /** 用例名；供 Catalog.resolve。 */
    String useCase;

    /** 会话 id；限流键。空白时装饰器用 {@code "_"}。 */
    String sessionId;

    String traceId;

    /** 可选；图超步 runId。 */
    String runId;

    Double temperature;

    Integer maxTokens;
}
