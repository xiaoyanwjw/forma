package com.xmut.ebus.application.business.agent.workspace;

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
}
