package com.xmut.forma.pi.agent.graph.checkpoint.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.forma.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.forma.pi.agent.graph.checkpoint.Checkpointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis Checkpointer 实现。
 * 功能描述：把图检查点存到 Redis（前缀 pi:checkpoint:）。
 * 关键设计：仅在显式开启时注册，不作为静默默认。
 */
public final class RedisCheckpointer implements Checkpointer {

    private static final Logger log = LoggerFactory.getLogger(RedisCheckpointer.class);

    public static final String KEY_PREFIX = "pi:checkpoint:";
    public static final int DEFAULT_TTL_SECONDS = 7200; // 2h，≥ 典型 HITL 等待窗口

    private final PiRedisCommands redis;
    private final CheckpointCodec codec;
    private final ObjectMapper idsMapper;
    private final int ttlSeconds;

    public RedisCheckpointer(PiRedisCommands redis, CheckpointCodec codec, int ttlSeconds) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.idsMapper = CheckpointCodec.defaultMapper();
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    public RedisCheckpointer(PiRedisCommands redis) {
        this(redis, new CheckpointCodec(), DEFAULT_TTL_SECONDS);
    }

    @Override
    public void save(Checkpoint checkpoint) {
        if (checkpoint == null || !StringUtils.hasText(checkpoint.getRunId())) {
            throw new IllegalArgumentException("checkpoint.runId required");
        }
        try {
            String json = codec.encode(checkpoint);
            String runId = checkpoint.getRunId();
            String cpId = checkpoint.getCheckpointId();

            redis.setex(latestKey(runId), ttlSeconds, json);
            redis.setex(historyKey(runId, cpId), ttlSeconds, json);
            appendId(runId, cpId);
        } catch (Exception e) {
            log.error("Failed to save hermes checkpoint runId={}", checkpoint.getRunId(), e);
            throw new IllegalStateException("Failed to save hermes checkpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Checkpoint> loadLatest(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Optional.empty();
        }
        String json = redis.get(latestKey(runId));
        return codec.decode(json);
    }

    @Override
    public Optional<Checkpoint> load(String runId, String checkpointId) {
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(checkpointId)) {
            return Optional.empty();
        }
        String json = redis.get(historyKey(runId, checkpointId));
        return codec.decode(json);
    }

    @Override
    public List<Checkpoint> listByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Collections.emptyList();
        }
        List<String> ids = readIds(runId);
        List<Checkpoint> out = new ArrayList<>();
        for (String id : ids) {
            codec.decode(redis.get(historyKey(runId, id))).ifPresent(out::add);
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public void deleteByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        List<String> ids = readIds(runId);
        for (String id : ids) {
            redis.del(historyKey(runId, id));
        }
        redis.del(latestKey(runId));
        redis.del(idsKey(runId));
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    private void appendId(String runId, String checkpointId) throws Exception {
        List<String> ids = new ArrayList<>(readIds(runId));
        if (!ids.contains(checkpointId)) {
            ids.add(checkpointId);
        }
        redis.setex(idsKey(runId), ttlSeconds, idsMapper.writeValueAsString(ids));
    }

    private List<String> readIds(String runId) {
        String json = redis.get(idsKey(runId));
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return idsMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse hermes checkpoint ids for runId={}: {}", runId, e.toString());
            return Collections.emptyList();
        }
    }

    static String latestKey(String runId) {
        return KEY_PREFIX + runId + ":latest";
    }

    static String idsKey(String runId) {
        return KEY_PREFIX + runId + ":ids";
    }

    static String historyKey(String runId, String checkpointId) {
        return KEY_PREFIX + runId + ":" + checkpointId;
    }
}
