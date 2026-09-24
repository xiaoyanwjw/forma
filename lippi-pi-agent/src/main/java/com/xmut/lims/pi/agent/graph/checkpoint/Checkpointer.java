package com.xmut.lims.pi.agent.graph.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Graph Checkpoint 存储端口 [Lippi HITL]。
 *
 * <p>默认生产路径：本模块 {@code redis.RedisCheckpointer}（前缀 {@code pi:checkpoint:}）。
 * 无 {@code JedisPool} 时回落 {@link InMemoryCheckpointer}。
 *
 * <p>键仅 {@code runId}（无 tenant 段）。≠ SessionStore；≠ 上游 CheckpointManager；
 * ≠ agent {@code RedisCheckpointStore}（前缀 {@code agent:checkpoint:}，无 {@link #deleteByRun}）。
 */
public interface Checkpointer {

    void save(Checkpoint checkpoint);

    Optional<Checkpoint> loadLatest(String runId);

    Optional<Checkpoint> load(String runId, String checkpointId);

    List<Checkpoint> listByRun(String runId);

    /** 删除某 run 的全部检查点（终态 SUCCESS/FAILED/CANCELLED 时清理；SUSPENDED 保留）。 */
    void deleteByRun(String runId);
}
