package com.xmut.lims.pi.agent.graph.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Graph Checkpoint 存储端口 [Lippi HITL]。
 *
 * <p>本模块过渡默认：{@link InMemoryCheckpointer}。Redis 仅当
 * {@code lims.pi.checkpoint.redis.enabled=true} 且存在 {@code JedisPool} 时注册
 * {@code redis.RedisCheckpointer}（前缀 {@code pi:checkpoint:}）。
 * Adam 生产目标：MySQL {@code pi_graph_checkpoint}（Story 2.8）。
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
