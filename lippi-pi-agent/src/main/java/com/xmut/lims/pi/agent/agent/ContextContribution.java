package com.xmut.lims.pi.agent.agent;

/**
 * Context 段可选贡献（{@code AGENTS.md} / {@code HERMES.md} 扩展）。
 *
 * <p>默认 {@link #NOOP}。Recall 不走本接口（见 {@link VolatileContribution}）。
 * <strong>禁止</strong>在本接口实现 ContextCompressor（51-8）。
 */
@FunctionalInterface
public interface ContextContribution {

    ContextContribution NOOP = base -> null;

    /**
     * @return 追加到 Context（缓存 system）的文本；null/空白忽略
     */
    String contribute(SystemPromptInput base);
}
