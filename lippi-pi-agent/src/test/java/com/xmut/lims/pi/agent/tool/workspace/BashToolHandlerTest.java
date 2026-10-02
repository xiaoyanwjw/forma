package com.xmut.lims.pi.agent.tool.workspace;

import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.xmut.lims.pi.agent.tool.workspace.WorkspaceToolTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashToolHandlerTest {

    @Test
    void bash_echo() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new BashToolHandler().handle(
                call("bash", "{\"command\":\"echo hi\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertTrue(r.isSuccess());
        assertTrue(r.getOutput().contains("hi"));
    }

    @Test
    void bash_failsWhenWorkspaceRootMissing() throws Exception {
        ToolResult r = new BashToolHandler().handle(
                call("bash", "{\"command\":\"echo hi\"}"),
                new ToolContext("r", "t", null, null));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("workspace root missing"));
    }

    @Test
    void bash_nonZeroExit_fails() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new BashToolHandler().handle(
                call("bash", "{\"command\":\"echo boom >&2; exit 7\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("exit 7"));
        assertTrue(r.getErrorMessage().contains("boom"));
    }

    @Test
    void scrubEnvironment_dropsPlantedSecrets() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", "true");
        builder.environment().put("DB_PASSWORD", "planted-db-secret");
        builder.environment().put("JWT_SECRET", "planted-jwt-secret");
        builder.environment().put("DEEPSEEK_API_KEY", "planted-api-key");
        BashToolHandler.scrubEnvironment(builder, run);
        assertFalse(builder.environment().containsKey("DB_PASSWORD"));
        assertFalse(builder.environment().containsKey("JWT_SECRET"));
        assertFalse(builder.environment().containsKey("DEEPSEEK_API_KEY"));
        assertFalse(builder.environment().containsValue("planted-db-secret"));
        assertFalse(builder.environment().containsValue("planted-jwt-secret"));
        assertFalse(builder.environment().containsValue("planted-api-key"));
        assertEquals("C.UTF-8", builder.environment().get("LANG"));
        assertEquals(run.toAbsolutePath().normalize().toString(), builder.environment().get("HOME"));
        assertTrue(builder.environment().containsKey("PATH"));
        assertEquals(3, builder.environment().size());
    }

    @Test
    void bash_childEnvOmitsInheritedSecretNames() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new BashToolHandler().handle(
                call("bash", "{\"command\":\"printenv\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertTrue(r.isSuccess(), r.getErrorMessage());
        String out = r.getOutput();
        assertFalse(out.contains("DB_PASSWORD"));
        assertFalse(out.contains("JWT_SECRET"));
        assertFalse(out.contains("DEEPSEEK_API_KEY"));
        assertTrue(out.contains("LANG=C.UTF-8"));
        assertTrue(out.contains("HOME="));
        assertTrue(out.contains("PATH="));
    }

    @Test
    void bash_timesOut() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new BashToolHandler(200).handle(
                call("bash", "{\"command\":\"sleep 5\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("command timed out"));
    }

    @Test
    void bash_truncatesLongStdout() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new BashToolHandler().handle(
                call("bash", "{\"command\":\"yes | head -c 80000\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertTrue(r.isSuccess(), r.getErrorMessage());
        assertTrue(r.getOutput().endsWith("\n...[truncated]"));
        assertTrue(r.getOutput().length() <= BashToolHandler.MAX_OUTPUT_CHARS + "\n...[truncated]".length());
    }

    @Test
    void cappedOutput_marksTruncation() {
        BashToolHandler.CappedOutput out = new BashToolHandler.CappedOutput();
        byte[] chunk = new byte[1024];
        Arrays.fill(chunk, (byte) 'a');
        int total = 0;
        while (total < BashToolHandler.MAX_OUTPUT_CHARS + 2048) {
            out.accept(chunk, chunk.length);
            total += chunk.length;
        }
        String text = out.toTruncatedString();
        assertTrue(text.endsWith("\n...[truncated]"));
        assertTrue(text.length() <= BashToolHandler.MAX_OUTPUT_CHARS + "\n...[truncated]".length());
    }
}
