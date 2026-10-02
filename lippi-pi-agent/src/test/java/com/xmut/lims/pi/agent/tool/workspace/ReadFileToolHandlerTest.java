package com.xmut.lims.pi.agent.tool.workspace;

import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.xmut.lims.pi.agent.tool.workspace.WorkspaceToolTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadFileToolHandlerTest {

    @Test
    void read_failsWhenWorkspaceRootMissing() throws Exception {
        ToolResult r = new ReadFileToolHandler().handle(
                call("read_file", "{\"path\":\"a.txt\"}"),
                new ToolContext("r", "t", null, null));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("workspace root missing"));
    }

    @Test
    void read_failsWhenFileTooLarge() throws Exception {
        Path run = Files.createTempDirectory("ws-");
        Path file = run.resolve("big.bin");
        Files.write(file, new byte[ReadFileToolHandler.MAX_BYTES + 1]);
        ToolResult r = new ReadFileToolHandler().handle(
                call("read_file", "{\"path\":\"big.bin\"}"),
                new ToolContext("r", "t", null, run.toString()));
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("file too large"));
    }
}
