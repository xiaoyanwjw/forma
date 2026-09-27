package com.xmut.lims.pi.agent.skill;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * Loads a skill {@code promptRef} body and, when the ref points at {@code SKILL.md},
 * appends sibling {@code references/*.md} (sorted by filename) so field contracts stay
 * out of the skill core without a second tool round-trip.
 */
public final class SkillPromptBodyLoader {

    private SkillPromptBodyLoader() {
    }

    public static Optional<String> load(String promptRef) {
        if (!StringUtils.hasText(promptRef)) {
            return Optional.empty();
        }
        try {
            org.springframework.core.io.ResourceLoader springLoader =
                    new org.springframework.core.io.DefaultResourceLoader();
            String location = normalizeLocation(promptRef.trim());
            Resource skillResource = springLoader.getResource(location);
            if (!skillResource.exists()) {
                return Optional.empty();
            }
            String body = readUtf8(skillResource);
            if (!StringUtils.hasText(body)) {
                return Optional.empty();
            }
            String withRefs = appendReferences(springLoader, location, body.trim());
            return Optional.of(withRefs.trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    static String normalizeLocation(String promptRef) {
        if (promptRef.startsWith("classpath:")
                || promptRef.startsWith("file:")
                || promptRef.startsWith("http")) {
            return promptRef;
        }
        return "classpath:" + promptRef;
    }

    static String appendReferences(
            org.springframework.core.io.ResourceLoader springLoader,
            String skillLocation,
            String skillBody) throws Exception {
        String lower = skillLocation.toLowerCase();
        if (!lower.endsWith("skill.md")) {
            return skillBody;
        }
        int cut = skillLocation.length() - "SKILL.md".length();
        // Preserve actual filename casing from location when possible.
        if (!skillLocation.regionMatches(true, cut, "SKILL.md", 0, "SKILL.md".length())) {
            return skillBody;
        }
        String baseDir = skillLocation.substring(0, cut);
        String pattern = toPatternLocation(baseDir + "references/*.md");
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(springLoader);
        Resource[] refs = resolver.getResources(pattern);
        if (refs == null || refs.length == 0) {
            return skillBody;
        }
        Arrays.sort(refs, Comparator.comparing(
                r -> r.getFilename() == null ? "" : r.getFilename(),
                String.CASE_INSENSITIVE_ORDER));
        StringBuilder sb = new StringBuilder(skillBody);
        for (Resource ref : refs) {
            if (ref == null || !ref.exists() || !ref.isReadable()) {
                continue;
            }
            String name = ref.getFilename() == null ? "reference.md" : ref.getFilename();
            String text = readUtf8(ref);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            sb.append("\n\n---\n\n# Reference: ").append(name).append("\n\n");
            sb.append(text.trim());
        }
        return sb.toString();
    }

    /**
     * Single-resource {@code classpath:} must become {@code classpath*:} for ant-style patterns.
     */
    static String toPatternLocation(String location) {
        if (location.startsWith("classpath*:")) {
            return location;
        }
        if (location.startsWith("classpath:")) {
            return "classpath*:" + location.substring("classpath:".length());
        }
        return location;
    }

    private static String readUtf8(Resource resource) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
