package com.example;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;

/**
 * 测试夹具：对应 {@code tools/fixture/demo_echo.tool.json} 的 handlerClass。
 * 存在此类后，扫盘 + AutowireCapableBeanFactory 可 createBean，避免拖垮 Spring 装配测。
 */
public final class DemoEchoHandler implements ToolHandler {

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        return ToolResult.ok(callId, "demo_echo", "echo");
    }
}
