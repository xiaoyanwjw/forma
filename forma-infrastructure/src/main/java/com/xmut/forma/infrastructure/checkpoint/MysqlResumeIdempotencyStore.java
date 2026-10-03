package com.xmut.forma.infrastructure.checkpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiResumeIdempotencyMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiResumeIdempotencyPO;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.ai.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adam 生产 resume 幂等实现（MySQL）。
 * 功能描述：对 (runId, confirmId) 原子占位，语义对齐 InMemory/Redis。
 * 关键设计：表 {@code pi_resume_idempotency} 与 CP/Session 分表；过期视同缺失；
 * 由 {@link MysqlResumeIdempotencyStoreConfiguration} 在无 Redis 幂等 bean 时注册。
 */
public class MysqlResumeIdempotencyStore implements ResumeIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlResumeIdempotencyStore.class);

    public static final int DEFAULT_TTL_SECONDS = 86400;

    private final PiResumeIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;

    public MysqlResumeIdempotencyStore(PiResumeIdempotencyMapper mapper, int ttlSeconds) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.objectMapper = CheckpointCodec.defaultMapper();
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    @Override
    public ClaimResult claim(String runId, String confirmId) {
        requireIds(runId, confirmId);
        return doClaim(runId.trim(), confirmId.trim(), true);
    }

    private ClaimResult doClaim(String runId, String confirmId, boolean retryOnExpired) {
        Instant now = Instant.now();
        PiResumeIdempotencyPO row = new PiResumeIdempotencyPO();
        row.setRunId(runId);
        row.setConfirmId(confirmId);
        row.setPhase("in_progress");
        row.setResultSummary(null);
        row.setUpdatedAt(now);
        row.setExpiresAt(now.plusSeconds(ttlSeconds));
        try {
            mapper.insert(row);
            return ClaimResult.claimed();
        } catch (DuplicateKeyException e) {
            return resolveConflict(runId, confirmId, retryOnExpired);
        } catch (DataAccessException e) {
            log.error("Failed to claim mysql resume idem runId={} confirmId={}", runId, confirmId, e);
            throw new IllegalStateException("Failed to claim mysql resume idempotency: " + e.getMessage(), e);
        }
    }

    private ClaimResult resolveConflict(String runId, String confirmId, boolean retryOnExpired) {
        try {
            PiResumeIdempotencyPO existing = mapper.selectByKey(runId, confirmId);
            if (existing == null || isExpired(existing)) {
                // UNIQUE 冲突后行已消失或过期：仅删仍过期行后重试一次（避免 concurrent complete 刷新 TTL 后误删）
                if (retryOnExpired) {
                    if (existing != null) {
                        mapper.deleteByKeyIfExpired(runId, confirmId, Instant.now());
                    }
                    return doClaim(runId, confirmId, false);
                }
                return ClaimResult.inProgress();
            }
            if ("completed".equals(existing.getPhase())) {
                ConversationResult completed = decodeCompleted(existing.getResultSummary());
                if (completed != null) {
                    return ClaimResult.completed(completed);
                }
                return ClaimResult.completed(ConversationResult.failed(runId,
                        "idempotent resume: corrupt cached result for confirmId=" + confirmId));
            }
            return ClaimResult.inProgress();
        } catch (DataAccessException e) {
            log.error("Failed to resolve mysql resume idem conflict runId={} confirmId={}",
                    runId, confirmId, e);
            throw new IllegalStateException(
                    "Failed to claim mysql resume idempotency: " + e.getMessage(), e);
        }
    }

    @Override
    public void complete(String runId, String confirmId, ConversationResult result) {
        requireIds(runId, confirmId);
        Instant now = Instant.now();
        PiResumeIdempotencyPO row = new PiResumeIdempotencyPO();
        row.setRunId(runId.trim());
        row.setConfirmId(confirmId.trim());
        row.setPhase("completed");
        row.setResultSummary(encodeSummary(result));
        row.setUpdatedAt(now);
        row.setExpiresAt(now.plusSeconds(ttlSeconds));
        try {
            mapper.upsert(row);
        } catch (DataAccessException e) {
            log.error("Failed to complete mysql resume idem runId={} confirmId={}", runId, confirmId, e);
            throw new IllegalStateException("Failed to complete mysql resume idempotency: " + e.getMessage(), e);
        }
    }

    @Override
    public void abandon(String runId, String confirmId) {
        if (!StringUtils.hasText(confirmId) || !StringUtils.hasText(runId)) {
            return;
        }
        try {
            mapper.deleteByKey(runId.trim(), confirmId.trim());
        } catch (DataAccessException e) {
            log.error("Failed to abandon mysql resume idem runId={} confirmId={}",
                    runId, confirmId, e);
            throw new IllegalStateException(
                    "Failed to abandon mysql resume idempotency: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        try {
            mapper.deleteByRunId(runId.trim());
        } catch (DataAccessException e) {
            log.error("Failed to deleteByRun mysql resume idem runId={}", runId, e);
            throw new IllegalStateException(
                    "Failed to deleteByRun mysql resume idempotency: " + e.getMessage(), e);
        }
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    private boolean isExpired(PiResumeIdempotencyPO row) {
        Instant expiresAt = row.getExpiresAt();
        // null 视同过期（脏数据）
        return expiresAt == null || expiresAt.isBefore(Instant.now());
    }

    private String encodeSummary(ConversationResult result) {
        try {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("phase", "completed");
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
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", m.getRole());
                        msg.put("content", m.getContent());
                        msg.put("toolCallId", m.getToolCallId());
                        msgs.add(msg);
                    }
                }
                dto.put("messages", msgs);
            }
            return objectMapper.writeValueAsString(dto);
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
            Map<String, Object> dto = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
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
                    Map<String, Object> msg = (Map<String, Object>) o;
                    messages.add(Message.builder()
                            .role((String) msg.get("role"))
                            .content((String) msg.get("content"))
                            .toolCallId((String) msg.get("toolCallId"))
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
            log.warn("Failed to decode mysql resume idem payload: {}", e.toString());
            return null;
        }
    }

    private static void requireIds(String runId, String confirmId) {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("runId required for resume idempotency");
        }
        if (!StringUtils.hasText(confirmId)) {
            throw new IllegalArgumentException("confirmId required for resume idempotency");
        }
    }
}
