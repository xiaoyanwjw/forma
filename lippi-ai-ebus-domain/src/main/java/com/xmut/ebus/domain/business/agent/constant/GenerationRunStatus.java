package com.xmut.ebus.domain.business.agent.constant;

/**
 * GenerationRun 生命周期状态。
 * <p>
 * 本故事空跑结束为 {@link #FAILED}（无可用成果）；{@link #SETTLED} 留给真实落库结算路径（2.4+）。
 */
public enum GenerationRunStatus {

    RUNNING,
    FAILED,
    SETTLED;

    public static GenerationRunStatus fromCode(String code) {
        return GenerationRunStatus.valueOf(code);
    }
}
