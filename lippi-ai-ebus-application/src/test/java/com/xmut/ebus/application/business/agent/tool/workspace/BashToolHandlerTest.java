package com.xmut.ebus.application.business.agent.tool.workspace;

import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.xmut.ebus.application.business.agent.tool.workspace.WorkspaceToolTestSupport.call;
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
}
