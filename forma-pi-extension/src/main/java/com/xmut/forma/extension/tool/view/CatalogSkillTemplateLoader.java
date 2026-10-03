package com.xmut.forma.extension.tool.view;

import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillPromptBodyLoader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * Resolves {@code promptRef} like {@code classpath:scenes/.../SKILL.md} to the skill
 * directory and reads a relative Mustache file from the classpath.
 */
public final class CatalogSkillTemplateLoader implements SkillTemplateLoader {

    private final SkillCatalog catalog;
    private final ResourceLoader resourceLoader;

    public CatalogSkillTemplateLoader(SkillCatalog catalog, ResourceLoader resourceLoader) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    @Override
    public String load(String activeSkillId, String templateRelativePath) {
        if (!StringUtils.hasText(activeSkillId)) {
            throw new IllegalArgumentException("active skill required");
        }
        String skillId = activeSkillId.trim();
        Optional<Skill> skill = catalog.resolve(skillId);
        if (!skill.isPresent() || !StringUtils.hasText(skill.get().getPromptRef())) {
            throw new IllegalArgumentException("skill not found: " + skillId);
        }
        String relative = normalizeTemplateRelative(templateRelativePath);
        String location = skillResourceBase(skill.get().getPromptRef()) + relative;
        Resource resource = resourceLoader.getResource(location);
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("template not found: " + relative);
        }
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("template not found: " + relative);
        }
    }

    static String skillResourceBase(String promptRef) {
        String location = SkillPromptBodyLoader.normalizeLocation(promptRef.trim());
        int slash = location.lastIndexOf('/');
        if (slash < 0 || slash >= location.length() - 1) {
            throw new IllegalArgumentException("skill promptRef has no directory: " + promptRef);
        }
        return location.substring(0, slash + 1);
    }

    static String normalizeTemplateRelative(String templateRelativePath) {
        if (!StringUtils.hasText(templateRelativePath)) {
            throw new IllegalArgumentException("template path required");
        }
        String rel = templateRelativePath.trim().replace('\\', '/');
        while (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        if (!StringUtils.hasText(rel)
                || rel.startsWith("/")
                || rel.contains("..")
                || rel.indexOf(':') >= 0) {
            throw new IllegalArgumentException("template path escapes skill directory");
        }
        return rel;
    }
}
