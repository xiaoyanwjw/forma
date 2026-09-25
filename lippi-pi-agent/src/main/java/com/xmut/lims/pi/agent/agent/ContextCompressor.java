package com.xmut.lims.pi.agent.agent;

/**
 * L2 上下文压缩端口（对齐上游 {@code ContextCompressor} 同名；Story 51-8 / FR22）。
 *
 * <p>压缩对象：Context 段文本（agents/hermes）与/或中间 history。
 * <strong>禁止</strong>静默丢掉 Stable（含 Core）；Variable 靠 invalidate+rebuild。
 *
 * <p>独立摘要 useCase 常量：{@link #USE_CASE_COMPRESSION}（不得占用默认对话 useCase）。
 */
public interface ContextCompressor {

    /** 可选 LLM 摘要路径的独立 useCase（Catalog 未配则 deterministic）。 */
    String USE_CASE_COMPRESSION = "pi.compression";

    /**
     * 恒等实现：原样返回，不改 messages / Context。
     */
    ContextCompressor NOOP = CompressionResult::unchanged;

    /**
     * 超阈时压缩；未超阈或 disabled 时返回 unchanged。
     *
     * <p>实现须吞掉内部失败并回落原样（或由调用方 try/catch）；
     * <strong>禁止</strong>因压缩失败导致整轮 turn FAILED。
     */
    CompressionResult compress(CompressionRequest request);
}
