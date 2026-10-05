package com.xmut.forma.pi.agent.tool.base;

import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.xmut.forma.pi.agent.tool.base.WorkspaceToolTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void write_usesConfiguredRootWhenWorkspaceNotOnContext() throws Exception {
        Path root = Files.createTempDirectory("ws-cfg-");
        com.xmut.forma.common.workspace.RunWorkspacePaths.bindRoot(root);
        try {
            ToolContext ctx = new ToolContext("run-1", "t", null, null, "sess-1");
            ToolResult w = new WriteFileToolHandler().handle(
                    call("write_file", "{\"path\":\"a.txt\",\"content\":\"ok\"}"), ctx);
            assertTrue(w.isSuccess());
            Path written = root.resolve("sessions").resolve("sess-1").resolve("run-1").resolve("a.txt");
            assertTrue(Files.isRegularFile(written));
        } finally {
            com.xmut.forma.common.workspace.RunWorkspacePaths.bindRoot(null);
        }
    }

    @Test
    void write_emptyContent_createsEmptyFile() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolContext ctx = new ToolContext("r", "t", null, run.toString());
        ToolResult w = new WriteFileToolHandler().handle(
                call("write_file", "{\"path\":\"empty.txt\",\"content\":\"\"}"), ctx);
        assertTrue(w.isSuccess());
        Path written = run.resolve("empty.txt");
        assertTrue(Files.isRegularFile(written));
        assertEquals(0, Files.size(written));
    }

    @Test
    void write_rejectsMissingContent() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        ToolResult r = new WriteFileToolHandler().handle(
                call("write_file", "{\"path\":\"a.txt\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
    }

    @Test
    void write_rejectsSymlinkEscape() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        Path outside = Files.createTempFile("ws-outside-", ".txt");
        Path link = run.resolve("leak");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "symbolic links not available: " + ex);
        }
        ToolResult r = new WriteFileToolHandler().handle(
                call("write_file", "{\"path\":\"leak\",\"content\":\"x\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("escapes"));
        assertEquals("", new String(Files.readAllBytes(outside)));
    }
}
