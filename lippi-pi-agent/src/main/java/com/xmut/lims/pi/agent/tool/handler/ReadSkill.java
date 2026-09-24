package com.xmut.lims.pi.agent.tool.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillManifest;
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
 * {@code read_skill}：按 skill_id 读取 Skill 正文（md / 内联 prompt）。
 *
 * <p>只读；不写 Registry。由 {@code tools/read-skill.tool.json#handlerClass} 自动装配
 *（仅依赖 {@link SkillConfig}）。正文加载逻辑内聚本类，不另挂 SkillReader Bean。
 */
public final class ReadSkill implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ReadSkill.class);

    public static final String TOOL_ID = "read_skill";
    public static final String ARG_SKILL_ID = "skill_id";

    private final SkillConfig skillConfig;
    private final ResourceLoader resourceLoader;

    /**
     * 生产 / {@code createBean}：仅依赖 {@link SkillConfig}（正文加载用默认 Classpath ResourceLoader）。
     */
    public ReadSkill(SkillConfig skillConfig) {
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
            return ToolResult.failed(callId, TOOL_ID, "SkillConfig unavailable");
        }
        Optional<SkillManifest> resolved = skillConfig.resolve(requested);
        if (!resolved.isPresent()) {
            return ToolResult.failed(callId, TOOL_ID, "skill not found: " + skillId);
        }
        SkillManifest manifest = resolved.get();
        Optional<String> body = resolveBody(manifest);
        if (!body.isPresent()) {
            return ToolResult.failed(callId, TOOL_ID,
                    "skill body unavailable: " + skillId
                            + (StringUtils.hasText(manifest.getPromptRef())
                            ? " (promptRef=" + manifest.getPromptRef() + ")"
                            : ""));
        }
        String header = "# Skill " + manifest.getId()
                + (StringUtils.hasText(manifest.getVersion()) ? " @" + manifest.getVersion() : "")
                + "\n\n";
        // 明确告知「已加载完毕」——从根上消掉模型读完又再调一次的冲动
        String footer = "\n\n---\n"
                + "[Skill loaded] You already have the full skill body above. "
                + "Produce the final JSON now. Do NOT call read_skill again.\n";
        return ToolResult.ok(callId, TOOL_ID, header + body.get() + footer);
    }

    /**
     * 优先 {@code promptRef}；失败不回落内联（避免读错文件却假装成功）。
     * 无 promptRef 时用 {@code skillsPrompt}。
     */
    public Optional<String> resolveBody(SkillManifest manifest) {
        if (manifest == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(manifest.getPromptRef())) {
            return loadRef(manifest.getPromptRef().trim(), manifest.getId());
        }
        if (StringUtils.hasText(manifest.getSkillsPrompt())) {
            return Optional.of(manifest.getSkillsPrompt().trim());
        }
        return Optional.empty();
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
