package com.xmut.lims.pi.agent.resource;

import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.ToolConfig;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PiResourceLoader 默认实现。
 * 功能描述：启动扫描 prompts，并委托既有 Skill/Tool 配置。
 */
public final class DefaultPiResourceLoader implements PiResourceLoader {

    public static final String DEFAULT_PROMPT_PATTERN = ClasspathPromptBootstrap.DEFAULT_PATTERN;

    private static final Logger log = LoggerFactory.getLogger(DefaultPiResourceLoader.class);

    private final ResourcePatternResolver resolver;
    private final SkillConfig skillConfig;
    private final ToolConfig toolConfig;
    private final List<String> extensionNames;

    private volatile Map<String, PromptTemplate> prompts;

    public DefaultPiResourceLoader() {
        this(new PathMatchingResourcePatternResolver(), null, null, Collections.emptyList());
    }

    public DefaultPiResourceLoader(ResourcePatternResolver resolver,
                                   SkillConfig skillConfig,
                                   ToolConfig toolConfig,
                                   List<String> extensionNames) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.skillConfig = skillConfig;
        this.toolConfig = toolConfig;
        this.extensionNames = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(extensionNames, "extensionNames")));
        this.prompts = ClasspathPromptBootstrap.load(this.resolver);
    }

    @Override
    public AgentResourceSnapshot snapshot() {
        return new AgentResourceSnapshot(
                skillIds(),
                toolIds(),
                new LinkedHashMap<>(prompts),
                extensionNames);
    }

    @Override
    public void reload() {
        this.prompts = ClasspathPromptBootstrap.load(resolver);
    }

    @Override
    public Optional<PromptTemplate> findPrompt(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(prompts.get(name.trim()));
    }

    @Override
    public SlashExpansion expandSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return SlashExpansion.unchanged(text);
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return SlashExpansion.unchanged(text);
        }
        int space = firstWhitespace(trimmed);
        String token = space < 0 ? trimmed.substring(1) : trimmed.substring(1, space);
        String rest = space < 0 ? "" : trimmed.substring(space).trim();
        if (!StringUtils.hasText(token)) {
            return SlashExpansion.unchanged(text);
        }
        if (token.regionMatches(true, 0, "skill:", 0, 6)) {
            String skillId = token.substring(6).trim();
            if (!StringUtils.hasText(skillId)) {
                return SlashExpansion.unchanged(text);
            }
            Optional<String> body = loadSkillBody(skillId);
            if (!body.isPresent()) {
                return SlashExpansion.unchanged(text);
            }
            String expanded = StringUtils.hasText(rest) ? body.get() + "\n" + rest : body.get();
            return SlashExpansion.of(expanded, skillId);
        }
        PromptTemplate template = prompts.get(token);
        if (template == null) {
            return SlashExpansion.unchanged(text);
        }
        String body = template.getBody() != null ? template.getBody() : "";
        String expanded = body.replace("$@", rest);
        return SlashExpansion.of(expanded, null);
    }

    private Optional<String> loadSkillBody(String skillId) {
        if (skillConfig == null) {
            return Optional.empty();
        }
        Optional<SkillManifest> resolved = skillConfig.resolve(skillId);
        if (!resolved.isPresent()) {
            return Optional.empty();
        }
        SkillManifest manifest = resolved.get();
        // 与 ReadSkillHandler 一致：写了 promptRef 则失败不回落，避免读错文件却假装成功。
        if (StringUtils.hasText(manifest.getPromptRef())) {
            return loadPromptRef(manifest.getPromptRef().trim(), skillId);
        }
        if (StringUtils.hasText(manifest.getSkillsPrompt())) {
            return Optional.of(manifest.getSkillsPrompt().trim());
        }
        return Optional.empty();
    }

    /**
     * 用 Spring FQCN ResourceLoader 读 promptRef，避免与本类撞名。
     */
    private Optional<String> loadPromptRef(String promptRef, String skillId) {
        try {
            org.springframework.core.io.ResourceLoader springLoader =
                    new org.springframework.core.io.DefaultResourceLoader();
            String location = promptRef.startsWith("classpath:")
                    || promptRef.startsWith("file:")
                    || promptRef.startsWith("http")
                    ? promptRef
                    : "classpath:" + promptRef;
            Resource resource = springLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("PiResourceLoader: promptRef not found skillId={} ref={}", skillId, promptRef);
                return Optional.empty();
            }
            try (InputStream in = resource.getInputStream()) {
                String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                if (!StringUtils.hasText(body)) {
                    return Optional.empty();
                }
                return Optional.of(body.trim());
            }
        } catch (Exception ex) {
            log.warn("PiResourceLoader: failed promptRef skillId={} ref={}: {}",
                    skillId, promptRef, ex.toString());
            return Optional.empty();
        }
    }

    private List<String> skillIds() {
        if (skillConfig == null || skillConfig.manifests() == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (SkillManifest m : skillConfig.manifests()) {
            if (m != null && StringUtils.hasText(m.getId())) {
                ids.add(m.getId());
            }
        }
        return ids;
    }

    private List<String> toolIds() {
        if (toolConfig == null || toolConfig.manifests() == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (ToolManifest m : toolConfig.manifests()) {
            if (m != null && StringUtils.hasText(m.getId())) {
                ids.add(m.getId());
            }
        }
        return ids;
    }

    private static int firstWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
