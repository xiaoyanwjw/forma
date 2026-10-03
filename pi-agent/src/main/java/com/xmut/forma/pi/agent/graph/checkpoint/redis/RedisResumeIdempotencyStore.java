package com.xmut.forma.pi.agent.graph.checkpoint.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.agent.ConversationResult;
import com.xmut.forma.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.forma.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.forma.pi.ai.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis resume 幂等实现。
 * 功能描述：用 SET NX EX 对 (runId, confirmId) 占位。
 */
public final class RedisResumeIdempotencyStore implements ResumeIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisResumeIdempotencyStore.class);

    public static final String KEY_PREFIX = "pi:resume-idem:";
    public static final int DEFAULT_TTL_SECONDS = 86400; // 24h，≥ 客户端重试窗口

    private final PiRedisCommands redis;
    private final ObjectMapper mapper;
    private final int ttlSeconds;

    public RedisResumeIdempotencyStore(PiRedisCommands redis, int ttlSeconds) {
        this.redis = redis;
        this.mapper = CheckpointCodec.defaultMapper();
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    public RedisResumeIdempotencyStore(PiRedisCommands redis) {
        this(redis, DEFAULT_TTL_SECONDS);
    }

    @Override
    public ClaimResult claim(String runId, String confirmId) {
        requireIds(runId, confirmId);
        return doClaim(runId, confirmId, true);
    }

    private ClaimResult doClaim(String runId, String confirmId, boolean retryOnNull) {
        String key = entryKey(runId, confirmId);
        String inProgressJson = encodePhase("in_progress", null);
        boolean claimed = redis.setIfAbsent(key, inProgressJson, ttlSeconds);
        if (claimed) {
            trackConfirmId(runId, confirmId);
            return ClaimResult.claimed();
        }

        String existing = redis.get(key);
        if (existing == null || existing.isEmpty()) {
            // SET NX 失败后键已过期：重试一次，避免误报 IN_PROGRESS
            if (retryOnNull) {
                return doClaim(runId, confirmId, false);
            }
            return ClaimResult.inProgress();
        }
        ConversationResult completed = decodeCompleted(existing);
        if (completed != null) {
            return ClaimResult.completed(completed);
        }
        if (looksCompleted(existing)) {
            // 摘要损坏：明确失败，禁止当成 IN_PROGRESS 挡死，也不重放 WRITE
            return ClaimResult.completed(ConversationResult.failed(runId,
                    "idempotent resume: corrupt cached result for confirmId=" + confirmId));
        }
        return ClaimResult.inProgress();
    }

    private boolean looksCompleted(String json) {
        try {
            Map<String, Object> dto = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return "completed".equals(dto.get("phase"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void complete(String runId, String confirmId, ConversationResult result) {
        requireIds(runId, confirmId);
        String key = entryKey(runId, confirmId);
        redis.setex(key, ttlSeconds, encodePhase("completed", result));
        trackConfirmId(runId, confirmId);
    }

    @Override
    public void abandon(String runId, String confirmId) {
        if (!StringUtils.hasText(confirmId)) {
            return;
        }
        redis.del(entryKey(runId, confirmId));
        untrackConfirmId(runId, confirmId);
    }

    @Override
    public void deleteByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        List<String> ids = readConfirmIds(runId);
        for (String id : ids) {
            redis.del(entryKey(runId, id));
        }
        redis.del(idsKey(runId));
    }

    private void trackConfirmId(String runId, String confirmId) {
        try {
            List<String> ids = new ArrayList<>(readConfirmIds(runId));
            if (!ids.contains(confirmId)) {
                ids.add(confirmId);
            }
            redis.setex(idsKey(runId), ttlSeconds, mapper.writeValueAsString(ids));
        } catch (Exception e) {
            log.warn("Failed to track resume idem id runId={}: {}", runId, e.toString());
        }
    }

    private void untrackConfirmId(String runId, String confirmId) {
        try {
            List<String> ids = new ArrayList<>(readConfirmIds(runId));
            ids.remove(confirmId);
            if (ids.isEmpty()) {
                redis.del(idsKey(runId));
            } else {
                redis.setex(idsKey(runId), ttlSeconds, mapper.writeValueAsString(ids));
            }
        } catch (Exception e) {
            log.warn("Failed to untrack resume idem id runId={}: {}", runId, e.toString());
        }
    }

    private List<String> readConfirmIds(String runId) {
        String json = redis.get(idsKey(runId));
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String encodePhase(String phase, ConversationResult result) {
        try {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("phase", phase);
            if (result != null) {
                dto.put("runId", result.getRunId());
                dto.put("status", result.getStatus() != null ? result.getStatus().name() : null);
                dto.put("finalResponse", result.getFinalResponse());
                List<Map<String, Object>> msgs = new ArrayList<>();
                if (result.getMessages() != null) {
                    for (Message m : result.getMessages()) {
                        if (m == null) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("role", m.getRole());
                        row.put("content", m.getContent());
                        row.put("toolCallId", m.getToolCallId());
                        msgs.add(row);
                    }
                }
                dto.put("messages", msgs);
            }
            return mapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new IllegalStateException("encode resume idem payload failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConversationResult decodeCompleted(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> dto = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (!"completed".equals(dto.get("phase"))) {
                return null;
            }
            String statusName = (String) dto.get("status");
            ConversationResult.Status status = statusName != null
                    ? ConversationResult.Status.valueOf(statusName)
                    : ConversationResult.Status.FAILED;
            List<Message> messages = new ArrayList<>();
            Object rawMsgs = dto.get("messages");
            if (rawMsgs instanceof List) {
                for (Object o : (List<?>) rawMsgs) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> row = (Map<String, Object>) o;
                    messages.add(Message.builder()
                            .role((String) row.get("role"))
                            .content((String) row.get("content"))
                            .toolCallId((String) row.get("toolCallId"))
                            .build());
                }
            }
            return ConversationResult.builder()
                    .runId((String) dto.get("runId"))
                    .status(status)
                    .finalResponse((String) dto.get("finalResponse"))
                    .messages(messages)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to decode resume idem payload: {}", e.toString());
            return null;
        }
    }

    private static void requireIds(String runId, String confirmId) {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("runId required for hermes resume idempotency");
        }
        if (!StringUtils.hasText(confirmId)) {
            throw new IllegalArgumentException("confirmId required for hermes resume idempotency");
        }
    }

    static String entryKey(String runId, String confirmId) {
        return KEY_PREFIX + runId + ":" + confirmId;
    }

    static String idsKey(String runId) {
        return KEY_PREFIX + runId + ":ids";
    }
}
