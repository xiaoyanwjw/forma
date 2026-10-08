package com.xmut.forma.boot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormaLocalEnvFileEnvironmentPostProcessorTest {

    @Test
    void parseLine_skipsCommentsAndReadsToken() {
        assertNull(FormaEnvPostProcessor.parseLine("# APIFY_TOKEN=nope"));
        FormaEnvPostProcessor.ParsedLine line =
                FormaEnvPostProcessor.parseLine("APIFY_TOKEN=abc123");
        assertEquals("APIFY_TOKEN", line.key);
        assertEquals("abc123", line.value);
    }

    @Test
    void postProcess_readsRepoRelativeEnvFile() throws Exception {
        Path root = Files.createTempDirectory("forma-env-root-");
        Path envDir = root.resolve("APP-META/docker-config/environment");
        Files.createDirectories(envDir);
        Files.write(envDir.resolve(".env"), "FORMA_TEST_ENV_KEY=from-file\n".getBytes(StandardCharsets.UTF_8));
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toAbsolutePath().toString());
        try {
            StandardEnvironment environment = new StandardEnvironment();
            new FormaEnvPostProcessor()
                    .postProcessEnvironment(environment, new SpringApplication());
            assertEquals("from-file", environment.getProperty("FORMA_TEST_ENV_KEY"));
            assertTrue(environment.getPropertySources().contains(
                    FormaEnvPostProcessor.PROPERTY_SOURCE_NAME));
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void parseDotEnv_ignoresCommentedTokenLine() throws Exception {
        Path file = Files.createTempFile("forma-env-", ".env");
        Files.write(file, "# APIFY_TOKEN=commented\nFOO=bar\n".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> parsed = FormaEnvPostProcessor.parseDotEnv(file);
        assertEquals("bar", parsed.get("FOO"));
        assertNull(parsed.get("APIFY_TOKEN"));
    }

    @Test
    void parseDotEnv_keepsUncommentedKeys() throws Exception {
        Path file = Files.createTempFile("forma-env-full-", ".env");
        Files.write(
                file,
                ("# comment\n"
                        + "MYSQL_PORT=3306\n"
                        + "APIFY_TOKEN=test-token-value\n").getBytes(StandardCharsets.UTF_8));
        Map<String, Object> parsed = FormaEnvPostProcessor.parseDotEnv(file);
        assertEquals(2, parsed.size());
        assertEquals("test-token-value", parsed.get("APIFY_TOKEN"));
    }

    @Test
    void postProcess_aliasesApifyTokenOntoWebFetchProperty() throws Exception {
        Path root = Files.createTempDirectory("forma-env-alias-");
        Path envDir = root.resolve("APP-META/docker-config/environment");
        Files.createDirectories(envDir);
        Files.write(envDir.resolve(".env"), "APIFY_TOKEN=from-file\n".getBytes(StandardCharsets.UTF_8));
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toAbsolutePath().toString());
        try {
            StandardEnvironment environment = new StandardEnvironment();
            new FormaEnvPostProcessor()
                    .postProcessEnvironment(environment, new SpringApplication());
            assertEquals("from-file", environment.getProperty("APIFY_TOKEN"));
            assertEquals("from-file", environment.getProperty("forma.web-fetch.apify.token"));
            assertEquals("from-file", environment.getProperty("forma.product-launch-search.apify.token"));
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void postProcess_fileTokenWinsOverEmptyProcessPlaceholder() throws Exception {
        Path root = Files.createTempDirectory("forma-env-empty-os-");
        Path envDir = root.resolve("APP-META/docker-config/environment");
        Files.createDirectories(envDir);
        Files.write(envDir.resolve(".env"), "APIFY_TOKEN=from-file\n".getBytes(StandardCharsets.UTF_8));
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toAbsolutePath().toString());
        try {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("ideEmptyEnv", Collections.singletonMap("APIFY_TOKEN", "")));
            new FormaEnvPostProcessor()
                    .postProcessEnvironment(environment, new SpringApplication());
            assertEquals("from-file", environment.getProperty("APIFY_TOKEN"));
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void findEnvFile_walksUpFromNestedWorkingDirectory() throws Exception {
        Path root = Files.createTempDirectory("forma-env-nested-");
        Path envDir = root.resolve("APP-META/docker-config/environment");
        Files.createDirectories(envDir);
        Files.write(envDir.resolve(".env"), "X=1\n".getBytes(StandardCharsets.UTF_8));
        Path nested = root.resolve("forma-starter");
        Files.createDirectories(nested);
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", nested.toAbsolutePath().toString());
        try {
            Path found = FormaEnvPostProcessor.findEnvFile();
            assertEquals(envDir.resolve(".env").toAbsolutePath().normalize(), found.toAbsolutePath().normalize());
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
