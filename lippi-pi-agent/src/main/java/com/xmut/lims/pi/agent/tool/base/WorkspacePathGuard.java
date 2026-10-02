package com.xmut.lims.pi.agent.tool.base;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ensures relative paths stay under a run workspace directory.
 */
public final class WorkspacePathGuard {

    private WorkspacePathGuard() {
    }

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
    static void assertNoSymlinkEscape(Path runDirNormalized, Path target) {
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
