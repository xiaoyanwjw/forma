package com.xmut.lims.pi.agent.tool.base;

import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.xmut.lims.pi.agent.tool.base.WorkspaceToolTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void resolveUnder_rejectsDotDotAndAbsolute() {
        Path run = Paths.get("/tmp/ws/sessions/s1/r1").toAbsolutePath().normalize();
        assertThrows(IllegalArgumentException.class,
                () -> WriteFileToolHandler.resolveUnder(run, "../x"));
        assertThrows(IllegalArgumentException.class,
                () -> WriteFileToolHandler.resolveUnder(run, "/etc/passwd"));
    }

    @Test
    void resolveUnder_acceptsNestedRelative() throws Exception {
        Path run = Files.createTempDirectory("ebus-ws-");
        Path out = WriteFileToolHandler.resolveUnder(run, "plan/final.json");
        assertTrue(out.startsWith(run));
        assertEquals("final.json", out.getFileName().toString());
    }

    @Test
    void resolveUnder_rejectsSymlinkEscape() throws Exception {
        Path run = Files.createTempDirectory("ebus-ws-");
        Path outside = Files.createTempFile("ebus-outside-", ".txt");
        try {
            Files.createSymbolicLink(run.resolve("leak"), outside);
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "symbolic links not available: " + ex);
        }
        assertThrows(IllegalArgumentException.class,
                () -> WriteFileToolHandler.resolveUnder(run, "leak"));
    }
}
