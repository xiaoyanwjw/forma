package com.xmut.lims.pi.agent.graph.checkpoint.redis;

/**
 * Checkpoint/幂等所需最小 Redis 命令集。
 * 功能描述：隔离具体 Redis 客户端 API。
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
