package com.xmut.forma.pi.agent.graph.checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Checkpointer。
 * 功能描述：进程内保存图检查点，作默认/测试回落。
 */
public final class InMemoryCheckpointer implements Checkpointer {

    private final Map<String, List<Checkpoint>> store = new ConcurrentHashMap<>();

    @Override
    public void save(Checkpoint checkpoint) {
        if (checkpoint == null || checkpoint.getRunId() == null) {
            throw new IllegalArgumentException("checkpoint.runId required");
        }
        store.computeIfAbsent(checkpoint.getRunId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(checkpoint);
    }

    @Override
    public Optional<Checkpoint> loadLatest(String runId) {
        List<Checkpoint> checkpoints = store.get(runId);
        if (checkpoints == null || checkpoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(checkpoints.get(checkpoints.size() - 1));
    }

    @Override
    public Optional<Checkpoint> load(String runId, String checkpointId) {
        List<Checkpoint> checkpoints = store.get(runId);
        if (checkpoints == null) {
            return Optional.empty();
        }
        return checkpoints.stream()
                .filter(cp -> cp.getCheckpointId().equals(checkpointId))
                .findFirst();
    }

    @Override
    public List<Checkpoint> listByRun(String runId) {
        List<Checkpoint> checkpoints = store.get(runId);
        return checkpoints != null
                ? Collections.unmodifiableList(new ArrayList<>(checkpoints))
                : Collections.emptyList();
    }

    @Override
    public void deleteByRun(String runId) {
        if (runId != null) {
            store.remove(runId);
        }
    }

    public void clear() {
        store.clear();
    }
}
