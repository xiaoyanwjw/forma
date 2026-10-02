package com.xmut.lims.pi.agent.tool.workspace;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pi tool {@code bash}: run a command with cwd = run workspace, timeout, truncated output.
 * The child still sees the host filesystem. Its environment is scrubbed to PATH, LANG, and HOME
 * so JVM secrets (DB, JWT, model keys) are not inherited.
 */
public final class BashToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(BashToolHandler.class);

    public static final String TOOL_NAME = "bash";
    public static final int TIMEOUT_MS = 30_000;
    public static final int MAX_OUTPUT_CHARS = 64 * 1024;
    static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin";

    private final int timeoutMs;

    public BashToolHandler() {
        this(TIMEOUT_MS);
    }

    /** Test hook so timeout coverage does not wait the production 30s. */
    BashToolHandler(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String root = WorkspaceToolSupport.workspaceRoot(ctx);
            if (root == null) {
                return ToolResult.failed(callId, TOOL_NAME, WorkspaceToolSupport.MISSING_ROOT);
            }
            String command = WorkspaceToolSupport.textArg(call, "command");
            if (!StringUtils.hasText(command)) {
                return ToolResult.failed(callId, TOOL_NAME, "command required");
            }
            Path runDir = Paths.get(root);
            ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", command);
            builder.directory(runDir.toFile());
            builder.redirectErrorStream(true);
            scrubEnvironment(builder, runDir);
            Process process = builder.start();
            CappedOutput capture = new CappedOutput();
            Thread reader = new Thread(copyStream(process.getInputStream(), capture), "bash-tool-stdout");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                return ToolResult.failed(callId, TOOL_NAME, "command timed out");
            }
            reader.join(TIMEOUT_MS);
            String output = capture.toTruncatedString();
            int exit = process.exitValue();
            if (exit != 0) {
                return ToolResult.failed(callId, TOOL_NAME, "exit " + exit + ": " + output);
            }
            return ToolResult.ok(callId, TOOL_NAME, output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.failed(callId, TOOL_NAME, "bash interrupted");
        } catch (Exception ex) {
            log.warn("bash failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "bash failed: " + ex.getMessage());
        }
    }

    /**
     * Start from an empty environment. Put back only PATH (inherited value, or a minimal default),
     * {@code LANG=C.UTF-8}, and {@code HOME} pointing at the run directory.
     */
    static void scrubEnvironment(ProcessBuilder builder, Path runDir) {
        Map<String, String> env = builder.environment();
        String path = env.get("PATH");
        if (!StringUtils.hasText(path)) {
            path = DEFAULT_PATH;
        }
        env.clear();
        env.put("PATH", path);
        env.put("LANG", "C.UTF-8");
        env.put("HOME", runDir.toAbsolutePath().normalize().toString());
    }

    private static Runnable copyStream(final InputStream in, final CappedOutput out) {
        return new Runnable() {
            @Override
            public void run() {
                byte[] chunk = new byte[4096];
                try {
                    int n;
                    while ((n = in.read(chunk)) >= 0) {
                        out.accept(chunk, n);
                    }
                } catch (IOException ignored) {
                    // process closed
                }
            }
        };
    }

    /**
     * Captures at most {@link #MAX_OUTPUT_CHARS} bytes; extra stdout is discarded, not buffered.
     */
    static final class CappedOutput {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        synchronized void accept(byte[] chunk, int n) {
            if (n <= 0) {
                return;
            }
            if (buffer.size() >= MAX_OUTPUT_CHARS) {
                truncated = true;
                return;
            }
            int allowed = MAX_OUTPUT_CHARS - buffer.size();
            int toWrite = Math.min(n, allowed);
            buffer.write(chunk, 0, toWrite);
            if (toWrite < n) {
                truncated = true;
            }
        }

        synchronized String toTruncatedString() {
            String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            if (truncated) {
                if (output.length() > MAX_OUTPUT_CHARS) {
                    output = output.substring(0, MAX_OUTPUT_CHARS);
                }
                return output + "\n...[truncated]";
            }
            return output;
        }
    }
}
