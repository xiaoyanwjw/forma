package com.xmut.lims.pi.agent.graph.checkpoint.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * {@link PiRedisCommands} 的 Jedis 实现。连接池由宿主应用提供（LIMS {@code JedisConfig}）。
 */
public final class JedisPiRedisCommands implements PiRedisCommands {

    private final JedisPool jedisPool;

    public JedisPiRedisCommands(JedisPool jedisPool) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("jedisPool required");
        }
        this.jedisPool = jedisPool;
    }

    @Override
    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public void setex(String key, int seconds, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, seconds, value);
        }
    }

    @Override
    public void del(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        long sec = ttlSeconds;
        if (sec < 1) {
            sec = 1;
        }
        if (sec > Integer.MAX_VALUE) {
            sec = Integer.MAX_VALUE;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = SetParams.setParams().nx().ex((int) sec);
            String result = jedis.set(key, value, params);
            return "OK".equals(result);
        }
    }
}
