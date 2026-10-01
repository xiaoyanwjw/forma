package com.xmut.ebus.application.business.agent.tool.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.ebus.application.business.agent.workspace.WorkspacePathGuard;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

final class WorkspaceToolSupport {

    static final String MISSING_ROOT = "workspace root missing";

    private WorkspaceToolSupport() {
    }

    static String workspaceRoot(ToolContext ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getWorkspaceRoot())) {
            return null;
        }
        return ctx.getWorkspaceRoot();
    }

    static String textArg(ToolCallEntry call, String field) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        JsonNode node = call.getArguments().get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    static Path resolve(String workspaceRoot, String relative) {
        return WorkspacePathGuard.resolveUnder(Paths.get(workspaceRoot), relative);
    }
}
