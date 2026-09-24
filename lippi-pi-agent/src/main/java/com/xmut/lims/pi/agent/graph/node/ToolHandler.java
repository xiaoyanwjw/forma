package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.ToolContext;

/**
 * hermes 最小工具执行端口（测试可注入假实现）。
 *
 * <p>签名使用 [LIMS] {@link ToolContext}；禁止复用 agent ToolHandler。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult handle(ToolCallEntry call, ToolContext ctx);
}
