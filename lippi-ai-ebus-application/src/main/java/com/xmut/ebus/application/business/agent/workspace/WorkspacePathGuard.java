package com.xmut.ebus.application.business.agent.workspace;

import com.xmut.ebus.common.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ensures relative paths stay under a run workspace directory.
 */
public final class WorkspacePathGuard {

    private WorkspacePathGuard() {
    }

    public static Path resolveUnder(Path runDir, String relative) {
        if (StringUtils.isBlank(relative)) {
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
        return normalized;
    }
}
