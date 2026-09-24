package com.xmut.lims.pi.agent.tool;

/**
 * [LIMS] 工具分级；上游 Hermes {@code tools/approval.py} 仅有危险命令 approval，无此四级。
 *
 * <p>禁止伪称 Hermes native 原语。
 */
public enum ToolLevel {

    /** 只读查询；执行前不挂起。 */
    READ,

    /** 建议类输出；约定 handler 禁止落业务库。 */
    SUGGEST,

    /** 写操作；未批准前必须 HITL 挂起。 */
    WRITE,

    /** 永拒；不调用 handler。 */
    FORBIDDEN
}
