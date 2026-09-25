package com.xmut.lims.pi.agent.agent;

/**
 * 上下文压缩端口。
 * 功能描述：在发模型前压缩 Context 段与/或中间 history。
 * 关键设计：禁止静默丢掉 Stable（含 Core）。
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
