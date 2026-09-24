package com.xmut.lims.pi.agent.agent;

/**
 * Volatiled 段可选贡献（memory / USER.md 扩展）。
 *
 * <p>注入点留给 51-7；默认 {@link #NOOP}。
 * <strong>禁止</strong>把本轮 user/tool 消息塞进本接口。
 */
@FunctionalInterface
public interface VolatileContribution {

    VolatileContribution NOOP = base -> null;

    /**
     * @return 追加到 variable（缓存 system）的文本；null/空白忽略
     */
    String contribute(SystemPromptInput base);
}
