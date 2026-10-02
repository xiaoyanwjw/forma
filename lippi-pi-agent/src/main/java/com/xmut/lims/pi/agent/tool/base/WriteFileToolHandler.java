package com.xmut.lims.pi.agent.tool.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pi tool {@code write_file}: write UTF-8 text under the run workspace.
 *
 * <p>Also hosts shared workspace path helpers used by {@link ReadFileToolHandler} /
 * {@link BashToolHandler} and application parsers.
 */
public final class WriteFileToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(WriteFileToolHandler.class);

    public static final String TOOL_NAME = "write_file";

    static final String MISSING_ROOT = "workspace root missing";

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String root = workspaceRoot(ctx);
            if (root == null) {
                return ToolResult.failed(callId, TOOL_NAME, MISSING_ROOT);
            }
            String path = textArg(call, "path");
            String content = textArg(call, "content");
            if (!StringUtils.hasText(path) || content == null) {
                return ToolResult.failed(callId, TOOL_NAME, "path and content required");
            }
            Path target = resolve(root, path);
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
        return node.asText(null);
    }

    static Path resolve(String workspaceRoot, String relative) {
        return resolveUnder(Paths.get(workspaceRoot), relative);
    }

    /**
     * Ensures relative paths stay under a run workspace directory.
     */
    public static Path resolveUnder(Path runDir, String relative) {
        if (!StringUtils.hasText(relative)) {
            throw new IllegalArgumentException("relative path required");
        }
        Path rel = Paths.get(relative);
        if (rel.isAbsolute()) {
            throw new IllegalArgumentException("absolute path not allowed");
        }
        Path normalizedRun = runDir.normalize();
        Path normalized = normalizedRun.resolve(relative).normalize();
        if (!normalized.startsWith(normalizedRun)) {
            throw new IllegalArgumentException("path escapes run directory");
        }
        assertNoSymlinkEscape(normalizedRun, normalized);
        return normalized;
    }

    /**
     * After lexical normalize, reject targets whose real path (or a symlink parent)
     * resolves outside the run directory.
     */
    private static void assertNoSymlinkEscape(Path runDirNormalized, Path target) {
        try {
            Path realRun = Files.exists(runDirNormalized)
                    ? runDirNormalized.toRealPath()
                    : runDirNormalized.toAbsolutePath().normalize();
            Path cursor = target;
            while (cursor != null) {
                if (Files.isSymbolicLink(cursor) || Files.exists(cursor)) {
                    Path real = cursor.toRealPath();
                    if (!real.startsWith(realRun)) {
                        throw new IllegalArgumentException("path escapes run directory");
                    }
                    break;
                }
                if (cursor.normalize().equals(runDirNormalized)) {
                    break;
                }
                cursor = cursor.getParent();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("path escapes run directory");
        }
    }
}
