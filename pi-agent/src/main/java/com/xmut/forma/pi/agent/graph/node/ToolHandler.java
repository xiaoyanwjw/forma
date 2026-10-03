package com.xmut.forma.pi.agent.graph.node;


import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import com.xmut.forma.pi.agent.tool.ToolContext;

/**
 * 工具执行端口。
 * 功能描述：执行单次 ToolCallEntry 并返回 ToolResult。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult handle(ToolCallEntry call, ToolContext ctx);
}
