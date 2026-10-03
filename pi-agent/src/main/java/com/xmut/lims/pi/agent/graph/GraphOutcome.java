package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;

/**
 * 图执行终态结果。
 * 功能描述：表达成功、失败或挂起等结局及最终状态。
 */
public final class GraphOutcome {

    public enum Kind {
        SUCCESS, FAILED, SUSPENDED, CANCELLED
    }

    private final Kind kind;
    private final GraphState finalState;
    private final String errorMessage;
    private final Throwable cause;
    private final String suspendedNode;
    private final Checkpoint suspendedCheckpoint;
    private final String cancelReason;

    private GraphOutcome(Kind kind, GraphState finalState, String errorMessage,
                         Throwable cause, String suspendedNode, Checkpoint suspendedCheckpoint,
                         String cancelReason) {
        this.kind = kind;
        this.finalState = finalState;
        this.errorMessage = errorMessage;
        this.cause = cause;
        this.suspendedNode = suspendedNode;
        this.suspendedCheckpoint = suspendedCheckpoint;
        this.cancelReason = cancelReason;
    }

    public static GraphOutcome success(GraphState state) {
        return new GraphOutcome(Kind.SUCCESS, state, null, null, null, null, null);
    }

    public static GraphOutcome failed(String message, Throwable cause) {
        return new GraphOutcome(Kind.FAILED, null, message, cause, null, null, null);
    }

    public static GraphOutcome suspended(String nodeName, Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint required for suspended outcome");
        }
        return new GraphOutcome(Kind.SUSPENDED, checkpoint.getState(), null, null, nodeName, checkpoint, null);
    }

    public static GraphOutcome cancelled(String reason) {
        return new GraphOutcome(Kind.CANCELLED, null, null, null, null, null, reason);
    }

    public Kind getKind() {
        return kind;
    }

    public GraphState getFinalState() {
        return finalState;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getCause() {
        return cause;
    }

    public String getSuspendedNode() {
        return suspendedNode;
    }

    public Checkpoint getSuspendedCheckpoint() {
        return suspendedCheckpoint;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public boolean isSuccess() {
        return kind == Kind.SUCCESS;
    }

    public boolean isFailed() {
        return kind == Kind.FAILED;
    }

    public boolean isSuspended() {
        return kind == Kind.SUSPENDED;
    }

    public boolean isCancelled() {
        return kind == Kind.CANCELLED;
    }
}
