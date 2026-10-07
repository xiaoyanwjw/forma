package com.xmut.forma.domain.business.agent.constant;

/**
 * GenerationRun 生命周期状态。
 */
public enum GenerationRunStatus {

    RUNNING,
    /** 无可用计费成果 */
    FAILED,
    SETTLED,
    /** 有成果、账未结清（预占已释放） */
    NEEDS_RECONCILE;

    public static GenerationRunStatus fromCode(String code) {
        return GenerationRunStatus.valueOf(code);
    }
}
