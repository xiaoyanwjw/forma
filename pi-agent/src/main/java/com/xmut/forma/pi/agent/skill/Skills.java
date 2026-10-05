package com.xmut.forma.pi.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill 加载工具（对齐官方 Pi {@code skills.ts}：parse 单文件 + loadFromClasspath 扫描）。
 */
public final class Skills {

    public static final String DEFAULT_PATTERN = "classpath*:scenes/*/*/SKILL.md";

    private static final Logger log = LoggerFactory.getLogger(Skills.class);

    private Skills() {}

    /** 解析单个 SKILL.md → {@link Skill}。 */
    public static Skill parse(Resource skillMd, String sceneCodeFromPath) throws IOException {
        if (skillMd == null) {
            throw new IllegalArgumentException("skillMd resource is null");
        }
        String text;
        try (InputStream in = skillMd.getInputStream()) {
            text = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        Map<String, String> frontmatter = parseFrontmatter(text);
        String name = frontmatter.get("name");
        String description = frontmatter.get("description");
        List<String> allowedTools = parseAllowedTools(frontmatter.get("allowed-tools"));
        String persistAs = readFrontmatterValue(text, "persistAs");
        List<String> hideFromHistory = parseAllowedTools(readFrontmatterValue(text, "hideFromHistory"));
        return Skill.builder()
                .id(name)
                .description(description)
                .promptRef(derivePromptRef(skillMd))
                .allowedTools(allowedTools)
                .sceneCode(sceneCodeFromPath)
                .persistAs(persistAs)
                .hideFromHistory(hideFromHistory)
                .output(readFrontmatterValue(text, "output"))
                .build();
    }

    public static int loadFromClasspath(SkillCatalog skillRegistry) {
        return loadFromClasspath(skillRegistry, new PathMatchingResourcePatternResolver(), DEFAULT_PATTERN);
    }

    public static int loadFromClasspath(SkillCatalog skillRegistry, ResourcePatternResolver resolver) {
        return loadFromClasspath(skillRegistry, resolver, DEFAULT_PATTERN);
    }

    /**
     * 扫描 classpath SKILL.md 并 {@link SkillCatalog#registerBootstrap}。
     *
     * @return 成功注册数量
     */
    public static int loadFromClasspath(SkillCatalog skillRegistry,
                                        ResourcePatternResolver resolver,
                                        String pattern) {
        if (skillRegistry == null) {
            throw new IllegalArgumentException("skillRegistry is null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is null");
        }
        String scan = StringUtils.hasText(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        Resource[] resources;
        try {
            resources = resolver.getResources(scan);
        } catch (Exception e) {
            throw new SkillValidationException(
                    "Skills: failed to scan pattern=" + scan, e);
        }
        if (resources.length == 0) {
            log.info("Skills: no SKILL.md under {}", scan);
            return 0;
        }
        int count = 0;
        Set<String> seenKeys = new LinkedHashSet<String>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String desc = describe(resource);
            try {
                Skill skill = parse(resource, sceneCodeFromPath(resource));
                String key = skill.getId();
                if (key == null || !seenKeys.add(key)) {
                    log.warn("Skills: skip duplicate or blank id {} from {}", key, desc);
                    continue;
                }
                skillRegistry.registerBootstrap(skill);
                count++;
                log.info("Skills: registered id={} scene={} from {}",
                        skill.getId(), skill.getSceneCode(), desc);
            } catch (SkillValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new SkillValidationException(
                        "Skills: failed to load " + desc, e);
            }
        }
        return count;
    }

    static String sceneCodeFromPath(Resource resource) {
        String relative = relativeClasspathPath(resource);
        if (!StringUtils.hasText(relative)) {
            return null;
        }
        String[] parts = relative.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length - 2; i++) {
            if ("scenes".equals(parts[i]) && StringUtils.hasText(parts[i + 1])) {
                return parts[i + 1];
            }
        }
        return null;
    }

    /**
     * 读 frontmatter 任意缩进的 {@code key:}（如 {@code metadata.persistAs}）。
     */
    static String readFrontmatterValue(String text, String key) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(key)) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return null;
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            if (normalized.endsWith("\n---")) {
                end = normalized.length() - 4;
            } else {
                return null;
            }
        }
        String block = normalized.substring(4, end);
        String prefix = key.trim() + ":";
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.startsWith(prefix)) {
                continue;
            }
            String value = trimmed.substring(prefix.length()).trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (isYamlBlockScalarIndicator(value)) {
                return null;
            }
            return unquote(value);
        }
        return null;
    }

    static Map<String, String> parseFrontmatter(String text) {
        if (text == null) {
            return Collections.emptyMap();
        }
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return Collections.emptyMap();
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            if (normalized.endsWith("\n---")) {
                end = normalized.length() - 4;
            } else {
                return Collections.emptyMap();
            }
        }
        String block = normalized.substring(4, end);
        Map<String, String> out = new LinkedHashMap<String, String>();
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; ) {
            String rawLine = lines[i];
            String trimmed = rawLine.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                i++;
                continue;
            }
            // 嵌套字段（如 metadata）跳过，只收顶层 key
            if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) {
                i++;
                continue;
            }
            int colon = rawLine.indexOf(':');
            if (colon <= 0) {
                i++;
                continue;
            }
            String key = rawLine.substring(0, colon).trim();
            String value = rawLine.substring(colon + 1).trim();
            if (!StringUtils.hasText(key)) {
                i++;
                continue;
            }
            if (isYamlBlockScalarIndicator(value)) {
                boolean folded = value.startsWith(">");
                i++;
                StringBuilder body = new StringBuilder();
                while (i < lines.length) {
                    String cont = lines[i];
                    String contTrim = cont.trim();
                    if (StringUtils.hasText(contTrim)
                            && !cont.startsWith(" ")
                            && !cont.startsWith("\t")) {
                        break;
                    }
                    if (!StringUtils.hasText(contTrim)) {
                        if (body.length() > 0) {
                            body.append('\n');
                        }
                        i++;
                        continue;
                    }
                    String content = cont.replaceFirst("^[ \\t]+", "");
                    if (folded) {
                        if (body.length() > 0 && body.charAt(body.length() - 1) != '\n') {
                            body.append(' ');
                        }
                        body.append(content);
                    } else {
                        if (body.length() > 0) {
                            body.append('\n');
                        }
                        body.append(content);
                    }
                    i++;
                }
                out.put(key, body.toString().trim());
            } else {
                out.put(key, unquote(value));
                i++;
            }
        }
        return out;
    }

    /** YAML block scalar 指示符：{@code >} / {@code >-} / {@code |} / {@code |-} 等。 */
    static boolean isYamlBlockScalarIndicator(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return ">".equals(value)
                || ">-".equals(value)
                || ">+".equals(value)
                || "|".equals(value)
                || "|-".equals(value)
                || "|+".equals(value);
    }

    static List<String> parseAllowedTools(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        // YAML: "ask_human, read_skill" / "ask_human read_skill" — 按逗号或空白切，勿把逗号粘进工具名
        String[] parts = raw.trim().split("[,\\s]+");
        List<String> out = new ArrayList<String>(parts.length);
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }

    static String derivePromptRef(Resource skillMd) {
        String relative = relativeClasspathPath(skillMd);
        if (StringUtils.hasText(relative)) {
            return "classpath:" + relative;
        }
        String filename = skillMd.getFilename();
        return StringUtils.hasText(filename) ? filename : skillMd.toString();
    }

    /**
     * 从 Resource 描述或 URL 推导 classpath 相对路径（优先 {@code scenes/...}）。
     */
    static String relativeClasspathPath(Resource skillMd) {
        if (skillMd == null) {
            return null;
        }
        String fromDescription = extractClasspathPath(skillMd.getDescription());
        if (StringUtils.hasText(fromDescription)) {
            return fromDescription;
        }
        try {
            String url = skillMd.getURL().toString().replace('\\', '/');
            int bang = url.indexOf("!/");
            if (bang >= 0 && bang + 2 < url.length()) {
                return url.substring(bang + 2);
            }
            String scenes = extractScenesRelative(url);
            if (StringUtils.hasText(scenes)) {
                return scenes;
            }
            String marker = ".jar/";
            int jar = url.indexOf(marker);
            if (jar >= 0) {
                return url.substring(jar + marker.length());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return extractScenesRelative(skillMd.getDescription());
    }

    static String extractScenesRelative(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.replace('\\', '/');
        int idx = normalized.indexOf("scenes/");
        if (idx < 0) {
            return null;
        }
        String rest = normalized.substring(idx);
        int q = rest.indexOf('?');
        if (q >= 0) {
            rest = rest.substring(0, q);
        }
        int close = rest.indexOf(']');
        if (close >= 0) {
            rest = rest.substring(0, close);
        }
        return rest;
    }

    private static String extractClasspathPath(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String prefix = "class path resource [";
        int start = description.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int open = start + prefix.length();
        int close = description.indexOf(']', open);
        if (close <= open) {
            return null;
        }
        String path = description.substring(open, close).trim();
        return StringUtils.hasText(path) ? path : null;
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
