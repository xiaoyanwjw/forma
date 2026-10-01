package com.xmut.ebus.application.business.agent.tool.workspace;

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
import java.util.concurrent.TimeUnit;

/**
 * Pi tool {@code bash}: run a command with cwd = run workspace, timeout, truncated output.
 */
public final class BashToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(BashToolHandler.class);

    public static final String TOOL_NAME = "bash";
    public static final int TIMEOUT_MS = 30_000;
    public static final int MAX_OUTPUT_CHARS = 64 * 1024;

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
            Process process = builder.start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread reader = new Thread(copyStream(process.getInputStream(), buffer), "bash-tool-stdout");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                return ToolResult.failed(callId, TOOL_NAME, "command timed out");
            }
            reader.join(TIMEOUT_MS);
            String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
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

    private static Runnable copyStream(final InputStream in, final ByteArrayOutputStream out) {
        return new Runnable() {
            @Override
            public void run() {
                byte[] chunk = new byte[4096];
                try {
                    int n;
                    while ((n = in.read(chunk)) >= 0) {
                        out.write(chunk, 0, n);
                    }
                } catch (IOException ignored) {
                    // process closed
                }
            }
        };
    }
}
