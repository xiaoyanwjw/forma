package com.xmut.lims.pi.agent.graph.checkpoint.redis;

/**
 * Checkpoint / resume 幂等所需的最小 Redis 命令。
 *
 * <p>语义对齐 LIMS {@code RedisClient} 的四个方法，便于单测 mock；
 * 不复制完整 Redis 客户端。pi-agent 不得依赖 infrastructure。
 */
public interface PiRedisCommands {

    String get(String key);

    void setex(String key, int seconds, String value);

    void del(String key);

    /**
     * 仅当 key 不存在时写入并设置 TTL（SET NX EX）。
     *
     * @return true 表示成功占位；false 表示 key 已存在
     */
    boolean setIfAbsent(String key, String value, long ttlSeconds);
}
