package com.xmut.forma.application.business.agent.workspace;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.workspace.RunWorkspacePaths;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Per-run workspace directories under {@code {root}/sessions/{sessionId}/{runId}/}.
 * Both ids must be a single safe path segment so a caller cannot escape {@code sessions/}.
 */
@Service
public class RunWorkspaceService {

    private final RunWorkspaceProperties properties;

    public RunWorkspaceService(RunWorkspaceProperties properties) {
        this.properties = properties;
    }

    public Path runDir(String sessionId, String runId) {
        try {
            return RunWorkspacePaths.runDir(properties.getRoot(), sessionId, runId);
        } catch (IllegalArgumentException ex) {
            throw invalidSegment();
        }
    }

    private static BusinessException invalidSegment() {
        return new BusinessException(ErrorCode.PARAM_INVALID, "invalid workspace path");
    }

    @SneakyThrows
    public Path ensureRunDir(String sessionId, String runId) {
        Path dir = runDir(sessionId, runId);
        Files.createDirectories(dir);
        return dir;
    }

    public void deleteRunDirQuietly(String sessionId, String runId) {
        Path dir = runDir(sessionId, runId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                            // quiet
                        }
                    });
        } catch (IOException ignored) {
            // quiet
        }
    }
}
