package com.xmut.ebus.application.business.agent.tool.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;

final class WorkspaceToolTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkspaceToolTestSupport() {
    }

    static ToolCallEntry call(String name, String json) throws Exception {
        ObjectNode args = (ObjectNode) MAPPER.readTree(json);
        return new ToolCallEntry("c1", name, args);
    }
}
