package com.xmut.lims.pi.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.lims.pi.ai.model.ToolSchema;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@code *.tool.json} → {@link ToolManifest}（classpath / 文件共用）。
 *
 * <p>不含 {@link com.xmut.lims.pi.agent.graph.node.ToolHandler}——若 JSON 含
 * {@code handlerClass}，由 {@link ToolHandlerAutoBinder} 自动创建并合并。
 */
public final class ToolManifestJsonLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolManifestJsonLoader() {}

    public static ToolManifest load(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("tool json input is null");
        }
        JsonNode n = MAPPER.readTree(in);
        String id = textOrNull(n, "id");
        if (id == null) {
            throw new ToolValidationException("tool json missing field: id");
        }
        String levelText = textOrNull(n, "level");
        if (levelText == null) {
            throw new ToolValidationException("tool json missing field: level");
        }
        ToolLevel level;
        try {
            level = ToolLevel.valueOf(levelText);
        } catch (IllegalArgumentException e) {
            throw new ToolValidationException("tool json invalid level: " + levelText, e);
        }

        ToolSchema schema = null;
        if (n.has("schema") && !n.get("schema").isNull()) {
            JsonNode s = n.get("schema");
            String name = textOrNull(s, "name");
            if (name == null) {
                name = id;
            }
            schema = ToolSchema.builder()
                    .name(name)
                    .description(textOrNull(s, "description"))
                    .parametersSchema(s.has("parametersSchema") && !s.get("parametersSchema").isNull()
                            ? s.get("parametersSchema")
                            : (s.has("parameters") && !s.get("parameters").isNull()
                            ? s.get("parameters") : null))
                    .build();
        }

        return ToolManifest.builder()
                .id(id)
                .version(textOrNull(n, "version"))
                .displayName(textOrNull(n, "displayName"))
                .description(textOrNull(n, "description"))
                .text(textOrNull(n, "text"))
                .schema(schema)
                .level(level)
                .handlerClass(textOrNull(n, "handlerClass"))
                .build();
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText();
        return t != null && !t.isEmpty() ? t : null;
    }
}
