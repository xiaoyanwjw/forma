package com.xmut.lims.pi.agent.graph.checkpoint;

import com.xmut.lims.pi.agent.ConversationResult;

/**
 * {@code (runId, confirmRequestId)} resume 幂等占位 [Lippi HITL]。
 *
 * <p>生产默认 Redis（{@code SET NX EX}）；内存实现仅单测 / 本地回落。
 * 键无 tenant。≠ SessionStore。
 */
public interface ResumeIdempotencyStore {

    enum ClaimStatus {
        /** 首次占位成功，调用方应执行 resume。 */
        CLAIMED,
        /** 已有完成摘要，应短路返回。 */
        COMPLETED,
        /** 其它调用方正在执行，勿重入。 */
        IN_PROGRESS
    }

    final class ClaimResult {
        private final ClaimStatus status;
        private final ConversationResult completedResult;

        private ClaimResult(ClaimStatus status, ConversationResult completedResult) {
            this.status = status;
            this.completedResult = completedResult;
        }

        public static ClaimResult claimed() {
            return new ClaimResult(ClaimStatus.CLAIMED, null);
        }

        public static ClaimResult completed(ConversationResult result) {
            return new ClaimResult(ClaimStatus.COMPLETED, result);
        }

        public static ClaimResult inProgress() {
            return new ClaimResult(ClaimStatus.IN_PROGRESS, null);
        }

        public ClaimStatus getStatus() {
            return status;
        }

        public ConversationResult getCompletedResult() {
            return completedResult;
        }
    }

    /**
     * 原子占位。{@code confirmRequestId} 为空时不应调用。
     */
    ClaimResult claim(String runId, String confirmRequestId);

    /** 终态成功 / 失败后写入摘要（覆盖 in_progress）。 */
    void complete(String runId, String confirmRequestId, ConversationResult result);

    /**
     * 释放占位（例如再次 SUSPENDED），允许同 confirmRequestId 后续重试。
     */
    void abandon(String runId, String confirmRequestId);

    /** 终态清理：删除该 run 下全部幂等键。 */
    void deleteByRun(String runId);
}
