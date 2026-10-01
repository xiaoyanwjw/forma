package com.xmut.ebus.application.business.agent.tool.workspace;

import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.xmut.ebus.application.business.agent.tool.workspace.WorkspaceToolTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileToolHandlerTest {

    @Test
    void writeThenRead_roundTrip() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        WriteFileToolHandler write = new WriteFileToolHandler();
        ReadFileToolHandler read = new ReadFileToolHandler();
        ToolContext ctx = new ToolContext("r", "t", null, run.toString());
        ToolResult w = write.handle(call("write_file", "{\"path\":\"a.json\",\"content\":\"{\\\"x\\\":1}\"}"), ctx);
        assertTrue(w.isSuccess());
        ToolResult r = read.handle(call("read_file", "{\"path\":\"a.json\"}"), ctx);
        assertTrue(r.getOutput().contains("\"x\""));
    }

    @Test
    void write_rejectsEscape() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new WriteFileToolHandler().handle(
                call("write_file", "{\"path\":\"../x\",\"content\":\"no\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
    }

    @Test
    void write_failsWhenWorkspaceRootMissing() throws Exception {
        ToolResult r = new WriteFileToolHandler().handle(
                call("write_file", "{\"path\":\"a.txt\",\"content\":\"x\"}"),
                new ToolContext("r", "t", null, null));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("workspace root missing"));
    }
}
