package com.xmut.ebus.application.business.agent.workspace;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Per-run workspace directories under {@code {root}/sessions/{sessionId}/{runId}/}.
 */
@Service
public class RunWorkspaceService {

    private final RunWorkspaceProperties properties;

    public RunWorkspaceService(RunWorkspaceProperties properties) {
        this.properties = properties;
    }

    public Path runDir(String sessionId, String runId) {
        return properties.getRoot()
                .resolve("sessions")
                .resolve(sessionId)
                .resolve(runId);
    }

    public Path ensureRunDir(String sessionId, String runId) throws IOException {
        Path dir = runDir(sessionId, runId);
        Files.createDirectories(dir);
        return dir;
    }

    public void deleteRunDirQuietly(String sessionId, String runId) {
        Path dir = runDir(sessionId, runId);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
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
