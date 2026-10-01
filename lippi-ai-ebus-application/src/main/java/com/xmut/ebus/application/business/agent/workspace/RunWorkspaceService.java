package com.xmut.ebus.application.business.agent.workspace;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Per-run workspace directories under {@code {root}/sessions/{sessionId}/{runId}/}.
 * Both ids must be a single safe path segment so a caller cannot escape {@code sessions/}.
 */
@Service
public class RunWorkspaceService {

    /** Single segment: UUID-like or letters, digits, dot, underscore, hyphen. Not {@code .} or {@code ..}. */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final RunWorkspaceProperties properties;

    public RunWorkspaceService(RunWorkspaceProperties properties) {
        this.properties = properties;
    }

    public Path runDir(String sessionId, String runId) {
        String session = requireSafeSegment(sessionId);
        String run = requireSafeSegment(runId);
        Path sessions = properties.getRoot().resolve("sessions").toAbsolutePath().normalize();
        Path dir = sessions.resolve(session).resolve(run).normalize();
        if (!dir.startsWith(sessions)) {
            throw invalidSegment();
        }
        return dir;
    }

    /**
     * Accept only one relative segment. Reject blank, {@code .}, {@code ..}, absolute paths,
     * separators, and anything outside the allowlist.
     */
    static String requireSafeSegment(String raw) {
        if (raw == null) {
            throw invalidSegment();
        }
        String segment = raw.trim();
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
            throw invalidSegment();
        }
        if (segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0) {
            throw invalidSegment();
        }
        Path asPath = Paths.get(segment);
        if (asPath.isAbsolute() || asPath.getNameCount() != 1) {
            throw invalidSegment();
        }
        if (!SAFE_SEGMENT.matcher(segment).matches()) {
            throw invalidSegment();
        }
        return segment;
    }

    private static BusinessException invalidSegment() {
        return new BusinessException(ErrorCode.PARAM_INVALID, "invalid workspace path");
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
