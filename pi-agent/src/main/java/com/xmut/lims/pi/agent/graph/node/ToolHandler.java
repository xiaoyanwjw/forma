package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.ToolContext;

/**
 * 工具执行端口。
 * 功能描述：执行单次 ToolCallEntry 并返回 ToolResult。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult handle(ToolCallEntry call, ToolContext ctx);
}
