package com.xmut.lims.pi.agent.tool.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePathGuardTest {

    @Test
    void rejectsDotDotAndAbsolute() {
        Path run = Paths.get("/tmp/ws/sessions/s1/r1").toAbsolutePath().normalize();
        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePathGuard.resolveUnder(run, "../x"));
        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePathGuard.resolveUnder(run, "/etc/passwd"));
    }

    @Test
    void acceptsNestedRelative() throws Exception {
        Path run = Files.createTempDirectory("ebus-ws-");
        Path out = WorkspacePathGuard.resolveUnder(run, "plan/final.json");
        assertTrue(out.startsWith(run));
        assertEquals("final.json", out.getFileName().toString());
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        Path run = Files.createTempDirectory("ebus-ws-");
        Path outside = Files.createTempFile("ebus-outside-", ".txt");
        try {
            Files.createSymbolicLink(run.resolve("leak"), outside);
        } catch (Exception ex) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links not available: " + ex);
        }
        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePathGuard.resolveUnder(run, "leak"));
    }
}
