package com.xmut.forma.extension.tool.view;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Pi tool {@code render_view}: fill the active skill Mustache template from artifact JSON
 * and write a Computer view v2 document.
 */
public final class RenderViewToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(RenderViewToolHandler.class);

    public static final String TOOL_NAME = "render_view";
    static final String DEFAULT_ARTIFACT = "artifact.json";
    static final String DEFAULT_OUT = "view.json";
    static final String DEFAULT_TEMPLATE = "template/view.mustache";

    private static final Map<String, String> SKILL_DEFAULT_FORMAT =
            Collections.unmodifiableMap(defaultSkillFormats());

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final SkillTemplateLoader templateLoader;
    private final MustacheViewRenderer renderer;
    private final ObjectMapper objectMapper;

    public RenderViewToolHandler(SkillTemplateLoader templateLoader, MustacheViewRenderer renderer) {
        this(templateLoader, renderer, new ObjectMapper());
    }

    RenderViewToolHandler(SkillTemplateLoader templateLoader, MustacheViewRenderer renderer,
                          ObjectMapper objectMapper) {
        this.templateLoader = Objects.requireNonNull(templateLoader, "templateLoader");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            if (ctx == null || !StringUtils.hasText(ctx.getWorkspaceRoot())) {
                return ToolResult.failed(callId, TOOL_NAME, "workspace root missing");
            }
            if (!StringUtils.hasText(ctx.getActiveSkillId())) {
                return ToolResult.failed(callId, TOOL_NAME, "active skill required");
            }
            String artifactRel = orDefault(textArg(call, "artifact"), DEFAULT_ARTIFACT);
            String outRel = orDefault(textArg(call, "out"), DEFAULT_OUT);
            String templateRel = orDefault(textArg(call, "template"), DEFAULT_TEMPLATE);
            String formatParam = textArg(call, "format");
            String formatResolved = StringUtils.hasText(formatParam)
                    ? formatParam.trim()
                    : orDefault(SKILL_DEFAULT_FORMAT.get(ctx.getActiveSkillId()), "html");
            Path workspace = Paths.get(ctx.getWorkspaceRoot());
            Path artifactPath = LocalFileSupport.resolveUnder(workspace, artifactRel);
            Path outPath = LocalFileSupport.resolveUnder(workspace, outRel);
            if (!Files.isRegularFile(artifactPath)) {
                throw new IllegalArgumentException("artifact file missing: " + artifactPath.getFileName());
            }
            Map<String, Object> data = readArtifactObject(artifactPath);
            data = ViewRenderHelpers.enrich(ctx.getActiveSkillId(), data);
            String content = renderer.render(templateLoader.load(ctx.getActiveSkillId(), templateRel), data);
            String title = data.get("title") instanceof String ? (String) data.get("title") : "draft";
            String format = "markdown".equalsIgnoreCase(formatResolved) ? "markdown" : "html";
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("version", Integer.valueOf(2));
            view.put("title", title);
            view.put("format", format);
            view.put("content", content);
            byte[] encoded = objectMapper.writeValueAsBytes(view);
            Path parent = outPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outPath, encoded);
            ObjectNode ok = objectMapper.createObjectNode();
            ok.put("ok", true);
            ok.put("out", outRel);
            ok.put("bytes", encoded.length);
            return ToolResult.ok(callId, TOOL_NAME, objectMapper.writeValueAsString(ok));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed(callId, TOOL_NAME, ex.getMessage());
        } catch (Exception ex) {
            log.warn("render_view failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "render_view failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> readArtifactObject(Path artifactPath) throws Exception {
        JsonNode root = objectMapper.readTree(Files.readAllBytes(artifactPath));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("artifact must be a JSON object");
        }
        return objectMapper.convertValue(root, MAP_TYPE);
    }

    private static Map<String, String> defaultSkillFormats() {
        return new LinkedHashMap<String, String>();
    }

    private static String orDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String textArg(ToolCallEntry call, String field) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        JsonNode node = call.getArguments().get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }
}
