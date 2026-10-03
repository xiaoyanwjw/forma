package com.xmut.forma.pi.ai.model.port;

/**
 * 模型调用限流反向端口 <b>[Lippi]</b>。
 *
 * <p>与业务配额语义分离：本端口仅约束模型调用次数，不替代业务层配额。
 */
public interface ModelRateLimit {

    /**
     * 尝试获取一次调用额度。
     *
     * @param sessionId 会话；空白时由装饰器归一为 {@code "_"}
     * @param useCase  用例
     * @param dailyLimit Catalog 配置的日限额；≤0 表示不限
     * @return true 允许调用
     */
    boolean acquire(String sessionId, String useCase, int dailyLimit);

    /** 无操作实现：始终放行。 */
    ModelRateLimit NOOP = (sessionId, useCase, dailyLimit) -> true;
}
