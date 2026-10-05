package com.xmut.forma.pi.agent.tool.base;

import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pi tool {@code write_file}: write UTF-8 text under the run workspace.
 */
public final class WriteFileToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(WriteFileToolHandler.class);

    public static final String TOOL_NAME = "write_file";

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String workspace = LocalFileSupport.workspace(ctx);
            if (workspace == null) {
                return ToolResult.failed(callId, TOOL_NAME, LocalFileSupport.MISSING_ROOT);
            }
            String path = LocalFileSupport.textArg(call, "path");
            String content = LocalFileSupport.textArg(call, "content");
            if (!StringUtils.hasText(path) || content == null) {
                return ToolResult.failed(callId, TOOL_NAME, "path and content required");
            }
            Path target = LocalFileSupport.resolve(workspace, path);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
            return ToolResult.ok(callId, TOOL_NAME, "wrote " + path);
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed(callId, TOOL_NAME, ex.getMessage());
        } catch (Exception ex) {
            log.warn("write_file failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "write_file failed: " + ex.getMessage());
        }
    }
}
