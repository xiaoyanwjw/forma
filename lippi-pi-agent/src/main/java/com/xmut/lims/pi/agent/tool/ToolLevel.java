package com.xmut.lims.pi.agent.tool;

/**
 * 工具分级枚举。
 * 功能描述：区分 READ / SUGGEST / WRITE / FORBIDDEN 的执行前策略。
 * 关键设计：≠ 上游仅危险命令 approval 的二元模型。
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
