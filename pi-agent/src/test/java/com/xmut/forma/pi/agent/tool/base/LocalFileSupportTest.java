package com.xmut.forma.pi.agent.tool.base;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileSupportTest {

    @Test
    void resolveUnder_rejectsDotDotAndAbsolute() {
        Path run = Paths.get("/tmp/ws/sessions/s1/r1").toAbsolutePath().normalize();
        assertThrows(IllegalArgumentException.class,
                () -> LocalFileSupport.resolveUnder(run, "../x"));
        assertThrows(IllegalArgumentException.class,
                () -> LocalFileSupport.resolveUnder(run, "/etc/passwd"));
    }

    @Test
    void resolveUnder_acceptsNestedRelative() throws Exception {
        Path run = Files.createTempDirectory("ebus-ws-");
        Path out = LocalFileSupport.resolveUnder(run, "plan/final.json");
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
                () -> LocalFileSupport.resolveUnder(run, "leak"));
    }
}
