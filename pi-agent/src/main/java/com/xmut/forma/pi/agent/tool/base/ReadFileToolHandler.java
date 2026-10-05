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
 * Pi tool {@code read_file}: read UTF-8 text under the run workspace (max {@link #MAX_BYTES}).
 */
public final class ReadFileToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ReadFileToolHandler.class);

    public static final String TOOL_NAME = "read_file";
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String workspace = LocalFileSupport.workspace(ctx);
            if (workspace == null) {
                return ToolResult.failed(callId, TOOL_NAME, LocalFileSupport.MISSING_ROOT);
            }
            String path = LocalFileSupport.textArg(call, "path");
            if (!StringUtils.hasText(path)) {
                return ToolResult.failed(callId, TOOL_NAME, "path required");
            }
            Path target = LocalFileSupport.resolve(workspace, path);
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                return ToolResult.failed(callId, TOOL_NAME, "file not found");
            }
            long size = Files.size(target);
            if (size > MAX_BYTES) {
                return ToolResult.failed(callId, TOOL_NAME, "file too large");
            }
            byte[] bytes = Files.readAllBytes(target);
            return ToolResult.ok(callId, TOOL_NAME, new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed(callId, TOOL_NAME, ex.getMessage());
        } catch (Exception ex) {
            log.warn("read_file failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "read_file failed: " + ex.getMessage());
        }
    }
}
