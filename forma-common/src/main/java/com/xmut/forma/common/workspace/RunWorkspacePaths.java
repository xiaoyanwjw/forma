package com.xmut.forma.common.workspace;

import com.xmut.forma.common.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Per-run workspace layout: {@code {root}/sessions/{sessionId}/{runId}/}.
 * 功能描述：读 {@code forma.workspace.root} / {@code FORMA_WORKSPACE_ROOT}，缺省 {@code ~/.forma}。
 * 关键设计：Spring 启动后可 {@link #bindRoot} 对齐配置文件；工具侧无入参时按 session+run 拼路径。
 */
public final class RunWorkspacePaths {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private static volatile Path boundRoot;

    private RunWorkspacePaths() {
    }

    /** Spring 把配置文件里的根目录绑进来；测试可清掉。 */
    public static void bindRoot(Path root) {
        boundRoot = root == null ? null : root.toAbsolutePath().normalize();
    }

    public static Path runDir(String sessionId, String runId) {
        return runDir(configuredRoot(), sessionId, runId);
    }

    public static Path runDir(Path root, String sessionId, String runId) {
        if (root == null) {
            throw new IllegalArgumentException("workspace root required");
        }
        String session = requireSafeSegment(sessionId);
        String run = requireSafeSegment(runId);
        Path sessions = root.toAbsolutePath().normalize().resolve("sessions");
        Path dir = sessions.resolve(session).resolve(run).normalize();
        if (!dir.startsWith(sessions)) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        return dir;
    }

    public static Path configuredRoot() {
        if (boundRoot != null) {
            return boundRoot;
        }
        String env = System.getenv("FORMA_WORKSPACE_ROOT");
        if (StringUtils.hasText(env)) {
            return Paths.get(env.trim()).toAbsolutePath().normalize();
        }
        String prop = System.getProperty("forma.workspace.root");
        if (StringUtils.hasText(prop)) {
            return Paths.get(prop.trim()).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".forma").toAbsolutePath().normalize();
    }

    static String requireSafeSegment(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        String segment = raw.trim();
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        if (segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        Path asPath = Paths.get(segment);
        if (asPath.isAbsolute() || asPath.getNameCount() != 1) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        if (!SAFE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("invalid workspace path");
        }
        return segment;
    }
}
