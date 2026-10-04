package com.xmut.forma.boot;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Loads {@code APP-META/docker-config/environment/.env} into the Spring Environment
 * so IDE Run/Debug picks up the same keys as a sourced shell.
 *
 * <p>Keys from the file are always parsed into the map (so a debugger {@code loaded}
 * size is the number of {@code KEY=value} lines, not zero). The source is added first
 * so a real token is not shadowed by an empty {@code APIFY_TOKEN=} in the IDE run
 * configuration. Docker usually has no such file and keeps using process env.
 */
public class FormaEnvPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "formaLocalEnvFile";
    static final String RELATIVE_ENV_FILE = "APP-META/docker-config/environment/.env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment == null) {
            return;
        }
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path file = findEnvFile();
        if (file == null) {
            return;
        }
        Map<String, Object> loaded;
        try {
            loaded = parseDotEnv(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + file, ex);
        }
        if (loaded.isEmpty()) {
            return;
        }
        applyWellKnownAliases(loaded);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, loaded));
    }

    /**
 * Scene-pack {@code application.yml} is loaded from extension jars by
 * {@code FormaExtensionApplicationYmlEnvironmentPostProcessor}. This processor
 * still aliases {@code APIFY_TOKEN} onto {@code forma.*.apify.token} so IDE
 * {@code .env} wins even if a placeholder was already resolved empty.
     */
    static void applyWellKnownAliases(Map<String, Object> loaded) {
        alias(loaded, "APIFY_TOKEN", new String[] {
                "forma.web-fetch.apify.token",
                "forma.sku-search.apify.token",
                "forma.xhs-note-search.apify.token",
                "forma.xhs-note-fetch.apify.token"
        });
        alias(loaded, "DEEPSEEK_API_KEY", new String[] {"ai.providers.deepseek.api-key"});
        alias(loaded, "DASHSCOPE_API_KEY", new String[] {"ai.providers.dashscope.api-key"});
    }

    static void alias(Map<String, Object> loaded, String fromKey, String[] toKeys) {
        Object value = loaded.get(fromKey);
        if (!(value instanceof String) || !StringUtils.hasText((String) value)) {
            return;
        }
        for (int i = 0; i < toKeys.length; i++) {
            if (!loaded.containsKey(toKeys[i])) {
                loaded.put(toKeys[i], value);
            }
        }
    }

    static Path findEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(RELATIVE_ENV_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    static Map<String, Object> parseDotEnv(Path file) throws IOException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                ParsedLine parsed = parseLine(line);
                if (parsed != null && StringUtils.hasText(parsed.value)) {
                    values.put(parsed.key, parsed.value);
                }
            }
        } finally {
            reader.close();
        }
        return values;
    }

    static ParsedLine parseLine(String raw) {
        if (raw == null) {
            return null;
        }
        String line = stripBom(raw).trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        if (line.startsWith("export ")) {
            line = line.substring("export ".length()).trim();
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = line.substring(0, eq).trim();
        if (!StringUtils.hasText(key) || key.startsWith("#")) {
            return null;
        }
        String value = unquote(line.substring(eq + 1).trim());
        return new ParsedLine(key, value);
    }

    private static String stripBom(String raw) {
        if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
            return raw.substring(1);
        }
        return raw;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    static final class ParsedLine {
        final String key;
        final String value;

        ParsedLine(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
