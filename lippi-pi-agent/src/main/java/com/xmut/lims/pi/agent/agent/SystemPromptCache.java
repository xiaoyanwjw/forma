package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.message.Message;

import java.util.Objects;

/**
 * 会话内可复用的 system prompt 缓存。
 *
 * <p>对齐 Hermes {@code agent._cached_system_prompt}：有则复用，无则
 * {@link PromptBuilder#system(SystemPromptInput)} 后写入。
 *
 * <p>缓存按 {@code runId} 分区：同 run 内超步复用；跨 run 必须重建，
 * 避免单例图上泄漏上一 run 的 Core/Recall system（AC4 / AC5）。
 *
 * @see PromptBuilder#system(SystemPromptInput)
 */
public final class SystemPromptCache {

    private String cachedKey;
    private Message cached;

    /**
     * 无 run 键时的兼容入口（等价于 {@code getOrBuild(builder, input, null)}）。
     */
    public Message getOrBuild(PromptBuilder builder, SystemPromptInput input) {
        return getOrBuild(builder, input, null);
    }

    /**
     * 有同键缓存则返回；否则用 {@code builder.system(input)} 构建并缓存。
     *
     * @param runId 本 run；同 run 复用，换 run 重建
     */
    public Message getOrBuild(PromptBuilder builder, SystemPromptInput input, String runId) {
        String key = cacheKey(runId);
        if (cached != null && Objects.equals(cachedKey, key)) {
            return cached;
        }
        if (builder == null) {
            throw new IllegalArgumentException("PromptBuilder required to build system prompt");
        }
        cached = builder.system(input != null ? input : SystemPromptInput.builder().build());
        cachedKey = key;
        return cached;
    }

    /**
     * 丢弃全部缓存，下次 {@link #getOrBuild} 重新构建。
     */
    public void invalidate() {
        cached = null;
        cachedKey = null;
    }

    /**
     * 仅当当前缓存键匹配 {@code runId} 时丢弃，避免共享单例误伤其它 run。
     */
    public void invalidate(String runId) {
        if (cached != null && Objects.equals(cachedKey, cacheKey(runId))) {
            invalidate();
        }
    }

    /** 当前缓存；未构建时为 {@code null}。 */
    public Message peek() {
        return cached;
    }

    static String cacheKey(String runId) {
        return nullToEmpty(runId);
    }

    private static String nullToEmpty(String raw) {
        return raw != null ? raw : "";
    }
}
