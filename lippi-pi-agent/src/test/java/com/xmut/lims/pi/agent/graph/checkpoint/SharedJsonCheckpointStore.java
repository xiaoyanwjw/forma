package com.xmut.lims.pi.agent.graph.checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用：多实例共享同一 backing map，save 经 {@link CheckpointCodec} 深拷贝（模拟跨 Pod Redis）。
 */
public final class SharedJsonCheckpointStore implements Checkpointer {

    private final ConcurrentHashMap<String, String> latestByRun;
    private final ConcurrentHashMap<String, List<String>> idsByRun;
    private final ConcurrentHashMap<String, String> history;
    private final CheckpointCodec codec;

    public SharedJsonCheckpointStore() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                new CheckpointCodec());
    }

    public SharedJsonCheckpointStore(ConcurrentHashMap<String, String> latestByRun,
                                    ConcurrentHashMap<String, List<String>> idsByRun,
                                    ConcurrentHashMap<String, String> history,
                                    CheckpointCodec codec) {
        this.latestByRun = latestByRun;
        this.idsByRun = idsByRun;
        this.history = history;
        this.codec = codec;
    }

    /** 另一「Pod」实例，共享同一内存 Redis。 */
    public SharedJsonCheckpointStore newPeer() {
        return new SharedJsonCheckpointStore(latestByRun, idsByRun, history, codec);
    }

    @Override
    public void save(Checkpoint checkpoint) {
        try {
            String json = codec.encode(checkpoint);
            String runId = checkpoint.getRunId();
            latestByRun.put(runId, json);
            history.put(runId + "|" + checkpoint.getCheckpointId(), json);
            idsByRun.computeIfAbsent(runId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(checkpoint.getCheckpointId());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<Checkpoint> loadLatest(String runId) {
        return codec.decode(latestByRun.get(runId));
    }

    @Override
    public Optional<Checkpoint> load(String runId, String checkpointId) {
        return codec.decode(history.get(runId + "|" + checkpointId));
    }

    @Override
    public List<Checkpoint> listByRun(String runId) {
        List<String> ids = idsByRun.getOrDefault(runId, Collections.emptyList());
        List<Checkpoint> out = new ArrayList<>();
        for (String id : ids) {
            load(runId, id).ifPresent(out::add);
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public void deleteByRun(String runId) {
        latestByRun.remove(runId);
        List<String> ids = idsByRun.remove(runId);
        if (ids != null) {
            for (String id : ids) {
                history.remove(runId + "|" + id);
            }
        }
    }

    public Map<String, String> latestSnapshot() {
        return Collections.unmodifiableMap(latestByRun);
    }
}
