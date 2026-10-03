package com.xmut.forma.pi.agent.graph.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * 图 Checkpoint 存储端口。
 * 功能描述：保存/加载某 run 的图挂起快照。
 * 关键设计：键仅为 runId；≠ SessionStore；Adam 生产默认 MySQL（MysqlCheckpointer）；本模块 MissingBean → InMemory。
 */
public interface Checkpointer {

    void save(Checkpoint checkpoint);

    Optional<Checkpoint> loadLatest(String runId);

    Optional<Checkpoint> load(String runId, String checkpointId);

    List<Checkpoint> listByRun(String runId);

    /** 删除某 run 的全部检查点（终态 SUCCESS/FAILED/CANCELLED 时清理；SUSPENDED 保留）。 */
    void deleteByRun(String runId);
}
