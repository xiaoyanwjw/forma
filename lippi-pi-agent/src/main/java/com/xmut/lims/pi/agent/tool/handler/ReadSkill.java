package com.xmut.lims.pi.agent.tool.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.skill.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * read_skill 工具实现。
 * 功能描述：按 skill_id 读取 Skill 正文（md / 内联 prompt）。
 * 关键设计：只读，不写 Registry。
 */
public final class ReadSkill implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ReadSkill.class);

    public static final String TOOL_ID = "read_skill";
    public static final String ARG_SKILL_ID = "skill_id";

    private final SkillCatalog skillConfig;
    private final ResourceLoader resourceLoader;

    /**
     * 生产 / {@code createBean}：仅依赖 {@link SkillCatalog}（正文加载用默认 Classpath ResourceLoader）。
     */
    public ReadSkill(SkillCatalog skillConfig) {
        this.skillConfig = skillConfig;
        this.resourceLoader = new DefaultResourceLoader();
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        String skillId = extractSkillId(call);
        if (!StringUtils.hasText(skillId)) {
            return ToolResult.failed(callId, TOOL_ID, "skill_id required");
        }
        String requested = skillId.trim();
        if (ctx != null && StringUtils.hasText(ctx.getActiveSkillId())
                && !requested.equals(ctx.getActiveSkillId().trim())) {
            return ToolResult.failed(callId, TOOL_ID,
                    "skill_id not active: " + requested + " (active=" + ctx.getActiveSkillId() + ")");
        }
        if (skillConfig == null) {
            return ToolResult.failed(callId, TOOL_ID, "SkillCatalog unavailable");
        }
        Optional<Skill> resolved = skillConfig.resolve(requested);
        if (!resolved.isPresent()) {
            return ToolResult.failed(callId, TOOL_ID, "skill not found: " + skillId);
        }
        Skill skill = resolved.get();
        Optional<String> body = resolveBody(skill);
        if (!body.isPresent()) {
            return ToolResult.failed(callId, TOOL_ID,
                    "skill body unavailable: " + skillId
                            + (StringUtils.hasText(skill.getPromptRef())
                            ? " (promptRef=" + skill.getPromptRef() + ")"
                            : ""));
        }
        String header = "# Skill " + skill.getId() + "\n\n";
        // 明确告知「已加载完毕」——从根上消掉模型读完又再调一次的冲动
        String footer = "\n\n---\n"
                + "[Skill loaded] You already have the full skill body above. "
                + "Produce the final JSON now. Do NOT call read_skill again.\n";
        return ToolResult.ok(callId, TOOL_ID, header + body.get() + footer);
    }

    /**
     * 仅通过 {@code promptRef} 读正文；失败不回落（避免读错文件却假装成功）。
     */
    public Optional<String> resolveBody(Skill skill) {
        if (skill == null || !StringUtils.hasText(skill.getPromptRef())) {
            return Optional.empty();
        }
        return loadRef(skill.getPromptRef().trim(), skill.getId());
    }

    private Optional<String> loadRef(String promptRef, String skillId) {
        try {
            Resource resource = resourceLoader.getResource(normalizeLocation(promptRef));
            if (!resource.exists()) {
                log.warn("read_skill: promptRef not found skillId={} ref={}", skillId, promptRef);
                return Optional.empty();
            }
            try (InputStream in = resource.getInputStream()) {
                String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                if (!StringUtils.hasText(body)) {
                    log.warn("read_skill: empty body skillId={} ref={}", skillId, promptRef);
                    return Optional.empty();
                }
                return Optional.of(body.trim());
            }
        } catch (Exception ex) {
            log.warn("read_skill: failed to load skillId={} ref={}: {}",
                    skillId, promptRef, ex.toString());
            return Optional.empty();
        }
    }

    /** 允许 {@code classpath:skills/x.md} 或裸路径 {@code skills/x.md}。 */
    static String normalizeLocation(String promptRef) {
        if (!StringUtils.hasText(promptRef)) {
            return promptRef;
        }
        String ref = promptRef.trim();
        if (ref.startsWith("classpath:") || ref.startsWith("file:") || ref.startsWith("http")) {
            return ref;
        }
        return "classpath:" + ref;
    }

    static String extractSkillId(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        JsonNode args = call.getArguments();
        if (args.has(ARG_SKILL_ID) && !args.get(ARG_SKILL_ID).isNull()) {
            return args.get(ARG_SKILL_ID).asText(null);
        }
        if (args.has("skillId") && !args.get("skillId").isNull()) {
            return args.get("skillId").asText(null);
        }
        return null;
    }
}
