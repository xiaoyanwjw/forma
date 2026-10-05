package com.xmut.forma.pi.agent.tool.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.forma.common.workspace.RunWorkspacePaths;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared helpers for local-file Pi tools ({@code write_file} / {@code read_file} / {@code bash})
 * and callers that must resolve paths under a run workspace.
 */
public final class LocalFileSupport {

    static final String MISSING_ROOT = "workspace root missing";

    private LocalFileSupport() {
    }

    public static String workspace(ToolContext ctx) {
        if (ctx != null && StringUtils.hasText(ctx.getWorkspaceRoot())) {
            return ctx.getWorkspaceRoot();
        }
        if (ctx == null) {
            return null;
        }
        try {
            return RunWorkspacePaths.runDir(ctx.getSessionId(), ctx.getRunId()).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
        requireNoSymlinkEscape(normalizedRun, normalized);
        return normalized;
    }

    /**
     * After lexical normalize, reject targets whose real path (or a symlink parent)
     * resolves outside the run directory.
     */
    private static void requireNoSymlinkEscape(Path runDirNormalized, Path target) {
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
