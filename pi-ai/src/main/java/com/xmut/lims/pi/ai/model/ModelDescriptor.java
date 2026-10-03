package com.xmut.lims.pi.ai.model;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

/**
 * 用例 → 物理模型描述 <b>[Lippi]</b>。
 *
 * <p>含 {@link #supportsNativeToolCalling}（ADR-C-08 语义落在 hermes）。
 * 模态用 {@link ModelModality}，禁止照搬 agent {@code Capability}。
 */
@Value
@Builder(toBuilder = true)
public class ModelDescriptor {

    String useCase;

    /** Provider 逻辑名（stub / dashscope 等）。 */
    String provider;

    /** 物理模型名。 */
    String model;

    @Builder.Default
    Set<ModelModality> modalities = Collections.singleton(ModelModality.CHAT);

    /**
     * {@code true} → 解析 native {@code tool_calls[]}；
     * {@code false} → 文本协议回落（ReAct 风格 Action / Action Input）。
     */
    @Builder.Default
    boolean supportsNativeToolCalling = true;

    /** 单租户每日调用配额；Decorator 限流用。null / ≤0 表示不限。 */
    Integer dailyQuotaPerTenant;

    /** 默认温度。 */
    Double temperature;

    /** 默认 maxTokens。 */
    Integer maxTokens;

    /**
     * DeepSeek V4 Chat Completions thinking 开关：{@code enabled} / {@code disabled}；
     * null 表示不传（跟随厂商默认，V4 默认为开启）。
     *
     * <p>结构化 JSON 输出 + tool 循环场景建议 {@code disabled}：thinking 默认开启时
     * reasoning 与 content 共享 max_tokens，易截断 JSON；且 tool 多轮须回传 reasoning_content。
     */
    String thinkingMode;

    /** 最大尝试次数（含首次）；Decorator 重试用。 */
    Integer maxAttempts;

    /**
     * Decorator LLM 响应缓存 TTL 秒；≤0 / null 表示不缓存。
     *
     * <p><b>与业务配额缓存语义分离</b>：本字段仅控制模型调用结果缓存，
     * 业务侧（如方案摘要缓存）不得复用同一 TTL 语义。
     */
    Integer cacheTtlSeconds;
}
