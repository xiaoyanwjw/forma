package com.xmut.lims.pi.agent.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 模板启动扫描器。
 * 功能描述：扫描 prompts/*.md 得到 PromptTemplate。
 */
public final class ClasspathPromptBootstrap {

    public static final String DEFAULT_PATTERN = "classpath*:prompts/*.md";

    private static final Logger log = LoggerFactory.getLogger(ClasspathPromptBootstrap.class);

    private ClasspathPromptBootstrap() {}

    public static Map<String, PromptTemplate> load() {
        return load(new PathMatchingResourcePatternResolver());
    }

    public static Map<String, PromptTemplate> load(ResourcePatternResolver resolver) {
        return load(resolver, DEFAULT_PATTERN);
    }

    public static Map<String, PromptTemplate> load(ResourcePatternResolver resolver, String pattern) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is null");
        }
        String scan = StringUtils.hasText(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        Resource[] resources;
        try {
            resources = resolver.getResources(scan);
        } catch (Exception e) {
            log.warn("ClasspathPromptBootstrap: failed to scan pattern={}: {}", scan, e.toString());
            return Collections.emptyMap();
        }
        if (resources == null || resources.length == 0) {
            log.info("ClasspathPromptBootstrap: no prompt md under {}", scan);
            return Collections.emptyMap();
        }
        Map<String, PromptTemplate> out = new LinkedHashMap<>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String filename = resource.getFilename();
            if (!StringUtils.hasText(filename) || !filename.endsWith(".md")) {
                continue;
            }
            String name = filename.substring(0, filename.length() - 3).trim();
            if (!StringUtils.hasText(name)) {
                log.warn("ClasspathPromptBootstrap: skip illegal empty name from {}", describe(resource));
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                PromptTemplate parsed = parseMarkdown(name, raw);
                if (parsed == null) {
                    log.warn("ClasspathPromptBootstrap: skip empty prompt {}", describe(resource));
                    continue;
                }
                if (out.containsKey(name)) {
                    log.warn("ClasspathPromptBootstrap: skip duplicate name={} from {}",
                            name, describe(resource));
                    continue;
                }
                out.put(name, parsed);
                log.info("ClasspathPromptBootstrap: loaded name={} from {}", name, describe(resource));
            } catch (Exception e) {
                log.warn("ClasspathPromptBootstrap: failed to load {}: {}",
                        describe(resource), e.toString());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * 手写两行 frontmatter：以 {@code ---} 起、下一 {@code ---} 止；只读 {@code description}。
     */
    static PromptTemplate parseMarkdown(String name, String raw) {
        if (raw == null) {
            return null;
        }
        String text = stripBom(raw).replace("\r\n", "\n");
        String description = null;
        String body = text;
        if (text.startsWith("---\n") || text.equals("---") || text.startsWith("---\r")) {
            int end = text.indexOf("\n---", 4);
            if (end >= 0) {
                String fm = text.substring(4, end);
                description = readDescription(fm);
                int bodyStart = end + 4;
                if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
                    bodyStart++;
                }
                body = bodyStart < text.length() ? text.substring(bodyStart) : "";
            }
        }
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return new PromptTemplate(name, description, body.replaceAll("\\s+$", ""));
    }

    private static String readDescription(String frontmatter) {
        if (frontmatter == null) {
            return null;
        }
        String[] lines = frontmatter.split("\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                String value = trimmed.substring("description:".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return StringUtils.hasText(value) ? value : null;
            }
        }
        return null;
    }

    private static String stripBom(String raw) {
        if (raw.length() > 0 && raw.charAt(0) == '\uFEFF') {
            return raw.substring(1);
        }
        return raw;
    }

    private static String describe(Resource resource) {
        try {
            String url = resource.getURL().toString();
            if (StringUtils.hasText(url)) {
                return url;
            }
        } catch (Exception ignored) {
            // fall through
        }
        String filename = resource.getFilename();
        return StringUtils.hasText(filename) ? filename : resource.toString();
    }
}
