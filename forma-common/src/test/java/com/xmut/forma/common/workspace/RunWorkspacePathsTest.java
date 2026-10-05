package com.xmut.forma.common.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunWorkspacePathsTest {

    @AfterEach
    void clearBind() {
        RunWorkspacePaths.bindRoot(null);
    }

    @Test
    void runDir_underSessions(@TempDir Path root) {
        Path dir = RunWorkspacePaths.runDir(root, "sess-1", "run-2");
        Path sessions = root.toAbsolutePath().normalize().resolve("sessions");
        assertTrue(dir.startsWith(sessions));
        assertEquals(sessions.resolve("sess-1").resolve("run-2"), dir);
    }

    @Test
    void bindRoot_usedWhenNoExplicitRoot(@TempDir Path root) {
        RunWorkspacePaths.bindRoot(root);
        Path dir = RunWorkspacePaths.runDir("s", "r");
        assertEquals(root.toAbsolutePath().normalize().resolve("sessions").resolve("s").resolve("r"), dir);
    }

    @Test
    void rejectsEscape() {
        assertThrows(IllegalArgumentException.class, () -> RunWorkspacePaths.runDir("ok", "../x"));
        assertThrows(IllegalArgumentException.class, () -> RunWorkspacePaths.runDir(null, "r"));
    }
}
