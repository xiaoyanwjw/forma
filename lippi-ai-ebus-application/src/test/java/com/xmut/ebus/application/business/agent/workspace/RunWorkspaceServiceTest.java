package com.xmut.ebus.application.business.agent.workspace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunWorkspaceServiceTest {

    @Test
    void ensureThenDelete() throws Exception {
        RunWorkspaceService svc = new RunWorkspaceService(propsWithTempRoot());
        Path dir = svc.ensureRunDir("s", "r");
        assertTrue(Files.isDirectory(dir));
        Files.write(dir.resolve("a.txt"), "x".getBytes(StandardCharsets.UTF_8));
        svc.deleteRunDirQuietly("s", "r");
        assertFalse(Files.exists(dir));
    }

    private static RunWorkspaceProperties propsWithTempRoot() throws Exception {
        Path root = Files.createTempDirectory("ebus-ws-root-");
        RunWorkspaceProperties props = new RunWorkspaceProperties();
        props.setRoot(root.toString());
        return props;
    }
}
