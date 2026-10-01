package com.xmut.ebus.application.business.agent.workspace;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunWorkspaceServiceTest {

    @Test
    void ensureThenDelete() throws Exception {
        Path root = Files.createTempDirectory("ebus-ws-root-");
        RunWorkspaceService svc = new RunWorkspaceService(props(root));
        Path dir = svc.ensureRunDir("s", "r");
        assertTrue(Files.isDirectory(dir));
        assertTrue(dir.startsWith(root.resolve("sessions").toAbsolutePath().normalize()));
        Files.write(dir.resolve("a.txt"), "x".getBytes(StandardCharsets.UTF_8));
        svc.deleteRunDirQuietly("s", "r");
        assertFalse(Files.exists(dir));
    }

    @Test
    void acceptsUuidLikeSegmentUnderSessions() throws Exception {
        Path root = Files.createTempDirectory("ebus-ws-root-");
        RunWorkspaceService svc = new RunWorkspaceService(props(root));
        Path dir = svc.ensureRunDir("550e8400-e29b-41d4-a716-446655440000", "run.1_ok");
        Path sessions = root.resolve("sessions").toAbsolutePath().normalize();
        assertTrue(dir.startsWith(sessions));
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void rejectsEscapeAttemptsAndDoesNotCreate() throws Exception {
        Path root = Files.createTempDirectory("ebus-ws-root-");
        RunWorkspaceService svc = new RunWorkspaceService(props(root));
        Path abs = Files.createTempDirectory("ebus-abs-");
        String[] bad = new String[] {
                "",
                " ",
                ".",
                "..",
                "../x",
                "foo/bar",
                "foo\\bar",
                abs.toString(),
                "C:\\Windows",
                "has space",
                "bad:name"
        };
        for (String segment : bad) {
            BusinessException sessionEx = assertThrows(BusinessException.class,
                    () -> svc.ensureRunDir(segment, "run-ok"));
            assertEquals(ErrorCode.PARAM_INVALID, sessionEx.getErrorCode());
            BusinessException runEx = assertThrows(BusinessException.class,
                    () -> svc.runDir("session-ok", segment));
            assertEquals(ErrorCode.PARAM_INVALID, runEx.getErrorCode());
        }
        assertThrows(BusinessException.class, () -> svc.runDir(null, "run-ok"));
        assertThrows(BusinessException.class, () -> svc.deleteRunDirQuietly("..", "run-ok"));

        assertFalse(Files.exists(root.resolve("x")));
        assertFalse(Files.exists(root.resolve("sessions").resolve("foo")));
        assertFalse(Files.exists(abs.resolve("run-ok")));

        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            longId.append('a');
        }
        assertThrows(BusinessException.class, () -> svc.runDir(longId.toString(), "run-ok"));
    }

    private static RunWorkspaceProperties props(Path root) {
        RunWorkspaceProperties props = new RunWorkspaceProperties();
        props.setRoot(root.toString());
        return props;
    }
}
