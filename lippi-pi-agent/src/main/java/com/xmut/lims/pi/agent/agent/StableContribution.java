package com.xmut.lims.pi.agent.agent;

/**
 * Stable 段可选贡献（soul / skills / tools 扩展）。
 *
 * <p>注入点留给 51-7；默认 {@link #NOOP}。
 * <strong>禁止</strong>在本接口实现 ContextCompressor（51-8）。
 */
@FunctionalInterface
public interface StableContribution {

    StableContribution NOOP = base -> null;

    /**
     * @return 追加到 Stable（缓存 system）的文本；null/空白忽略
     */
    String contribute(SystemPromptInput base);
}
