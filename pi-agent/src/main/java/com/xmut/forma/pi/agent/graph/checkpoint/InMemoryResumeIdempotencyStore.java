package com.xmut.forma.pi.agent.graph.checkpoint;

import com.xmut.forma.pi.agent.ConversationResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 resume 幂等实现。
 * 功能描述：用内存 map 做占位；跨 Pod 不可用。
 */
public final class InMemoryResumeIdempotencyStore implements ResumeIdempotencyStore {

    private static final class Entry {
        volatile String phase; // in_progress | completed
        volatile ConversationResult result;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> runKeys =
            new ConcurrentHashMap<>();

    @Override
    public ClaimResult claim(String runId, String confirmId) {
        String key = key(runId, confirmId);
        Entry created = new Entry();
        created.phase = "in_progress";
        Entry existing = entries.putIfAbsent(key, created);
        if (existing == null) {
            track(runId, key);
            return ClaimResult.claimed();
        }
        if ("completed".equals(existing.phase) && existing.result != null) {
            return ClaimResult.completed(existing.result);
        }
        return ClaimResult.inProgress();
    }

    @Override
    public void complete(String runId, String confirmId, ConversationResult result) {
        String key = key(runId, confirmId);
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        entry.result = result;
        entry.phase = "completed";
        track(runId, key);
    }

    @Override
    public void abandon(String runId, String confirmId) {
        String key = key(runId, confirmId);
        entries.remove(key);
        Map<String, Boolean> set = runKeys.get(runKey(runId));
        if (set != null) {
            set.remove(key);
        }
    }

    @Override
    public void deleteByRun(String runId) {
        ConcurrentHashMap<String, Boolean> set = runKeys.remove(runKey(runId));
        if (set != null) {
            for (String k : set.keySet()) {
                entries.remove(k);
            }
        }
    }

    public void clear() {
        entries.clear();
        runKeys.clear();
    }

    private void track(String runId, String key) {
        runKeys.computeIfAbsent(runKey(runId), k -> new ConcurrentHashMap<>())
                .put(key, Boolean.TRUE);
    }

    private static String key(String runId, String confirmId) {
        return nullToEmpty(runId) + "|" + nullToEmpty(confirmId);
    }

    private static String runKey(String runId) {
        return nullToEmpty(runId);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
