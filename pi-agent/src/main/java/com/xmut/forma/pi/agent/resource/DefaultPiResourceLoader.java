package com.xmut.forma.pi.agent.resource;

import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.tool.ToolCatalog;
import com.xmut.forma.pi.agent.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

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
 * 关键设计：{@link #reload()} 只重扫 prompts；Skill 由启动 {@code Skills} 装载并 seal，不热更。
 */
public final class DefaultPiResourceLoader implements PiResourceLoader {

    public static final String DEFAULT_PROMPT_PATTERN = ClasspathPromptBootstrap.DEFAULT_PATTERN;

    private static final Logger log = LoggerFactory.getLogger(DefaultPiResourceLoader.class);

    private final ResourcePatternResolver resolver;
    private final SkillCatalog skillConfig;
    private final ToolCatalog toolConfig;
    private final List<String> extensionNames;

    private volatile Map<String, PromptTemplate> prompts;

    public DefaultPiResourceLoader() {
        this(new PathMatchingResourcePatternResolver(), null, null, Collections.emptyList());
    }

    public DefaultPiResourceLoader(ResourcePatternResolver resolver,
                                   SkillCatalog skillConfig,
                                   ToolCatalog toolConfig,
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
        Optional<Skill> resolved = skillConfig.resolve(skillId);
        if (!resolved.isPresent()) {
            return Optional.empty();
        }
        Skill skill = resolved.get();
        // 与 ReadSkillHandler 一致：写了 promptRef 则失败不回落，避免读错文件却假装成功。
        if (StringUtils.hasText(skill.getPromptRef())) {
            return loadPromptRef(skill.getPromptRef().trim(), skillId);
        }
        return Optional.empty();
    }

    /**
     * 读 promptRef；{@code SKILL.md} 时附带同级 {@code references/*.md}（与 {@code read_skill} 一致）。
     */
    private Optional<String> loadPromptRef(String promptRef, String skillId) {
        Optional<String> body = com.xmut.forma.pi.agent.skill.SkillPromptBodyLoader.load(promptRef);
        if (!body.isPresent()) {
            log.warn("PiResourceLoader: promptRef unavailable skillId={} ref={}", skillId, promptRef);
        }
        return body;
    }

    private List<String> skillIds() {
        if (skillConfig == null || skillConfig.all() == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (Skill m : skillConfig.all()) {
            if (m != null && StringUtils.hasText(m.getId())) {
                ids.add(m.getId());
            }
        }
        return ids;
    }

    private List<String> toolIds() {
        if (toolConfig == null || toolConfig.all() == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (ToolDefinition m : toolConfig.all()) {
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
