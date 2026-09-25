package com.xmut.ebus.infrastructure.checkpoint;

import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiGraphCheckpointMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiGraphCheckpointPO;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adam 生产图 Checkpointer（MySQL）。
 * 功能描述：把 HITL 挂起快照落盘到 {@code pi_graph_checkpoint}（一行一 run）。
 * 关键设计：≠ Session 表；过期视同缺失；由 {@link MysqlCheckpointerConfiguration}
 * 在无 RedisCheckpointer bean 时注册。
 */
public class MysqlCheckpointer implements Checkpointer {

    private static final Logger log = LoggerFactory.getLogger(MysqlCheckpointer.class);

    public static final int DEFAULT_TTL_SECONDS = 7200;

    private final PiGraphCheckpointMapper mapper;
    private final CheckpointCodec codec;
    private final int ttlSeconds;

    public MysqlCheckpointer(PiGraphCheckpointMapper mapper, int ttlSeconds) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codec = new CheckpointCodec();
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    @Override
    public void save(Checkpoint checkpoint) {
        if (checkpoint == null || !StringUtils.hasText(checkpoint.getRunId())) {
            throw new IllegalArgumentException("checkpoint.runId required");
        }
        try {
            String json = codec.encode(checkpoint);
            Instant now = Instant.now();
            PiGraphCheckpointPO row = new PiGraphCheckpointPO();
            row.setRunId(checkpoint.getRunId().trim());
            row.setCheckpointId(checkpoint.getCheckpointId());
            row.setGraphState(json);
            row.setUpdatedAt(now);
            row.setExpiresAt(now.plusSeconds(ttlSeconds));
            mapper.upsert(row);
        } catch (DataAccessException e) {
            log.error("Failed to save mysql checkpoint runId={}", checkpoint.getRunId(), e);
            throw new IllegalStateException("Failed to save mysql checkpoint: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to encode/save mysql checkpoint runId={}", checkpoint.getRunId(), e);
            throw new IllegalStateException("Failed to save mysql checkpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Checkpoint> loadLatest(String runId) {
        return loadRow(runId).flatMap(row -> codec.decode(row.getGraphState()));
    }

    @Override
    public Optional<Checkpoint> load(String runId, String checkpointId) {
        if (!StringUtils.hasText(checkpointId)) {
            return Optional.empty();
        }
        return loadRow(runId)
                .filter(row -> checkpointId.equals(row.getCheckpointId()))
                .flatMap(row -> codec.decode(row.getGraphState()));
    }

    @Override
    public List<Checkpoint> listByRun(String runId) {
        return loadLatest(runId)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public void deleteByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        mapper.deleteByRunId(runId.trim());
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    private Optional<PiGraphCheckpointPO> loadRow(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Optional.empty();
        }
        PiGraphCheckpointPO row = mapper.selectByRunId(runId.trim());
        if (row == null) {
            return Optional.empty();
        }
        Instant expiresAt = row.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(row.getGraphState())) {
            return Optional.empty();
        }
        return Optional.of(row);
    }
}
