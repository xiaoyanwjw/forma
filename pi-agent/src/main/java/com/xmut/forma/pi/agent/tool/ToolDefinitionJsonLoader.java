package com.xmut.forma.pi.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@code *.tool.json} 扫盘解析 {@link ToolDefinition}。
 * 功能描述：schema 真源为 classpath 资源；不绑定 Handler。
 */
public final class ToolDefinitionJsonLoader {

    public static final String DEFAULT_PATTERN = "classpath*:tools/**/*.tool.json";

    private static final Logger log = LoggerFactory.getLogger(ToolDefinitionJsonLoader.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolDefinitionJsonLoader() {}

    /**
     * 扫描 {@link #DEFAULT_PATTERN} 并解析为定义列表（注册序 = 扫盘序）。
     */
    public static List<ToolDefinition> load(ResourcePatternResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is null");
        }
        Resource[] resources;
        try {
            resources = resolver.getResources(DEFAULT_PATTERN);
        } catch (Exception e) {
            throw new ToolValidationException(
                    "ToolDefinitionJsonLoader: failed to scan pattern=" + DEFAULT_PATTERN, e);
        }
        List<ToolDefinition> out = new ArrayList<ToolDefinition>();
        Set<String> seen = new LinkedHashSet<String>();
        if (resources == null || resources.length == 0) {
            log.info("ToolDefinitionJsonLoader: no *.tool.json under {}", DEFAULT_PATTERN);
            return out;
        }
        for (int i = 0; i < resources.length; i++) {
            Resource resource = resources[i];
            if (resource == null || !resource.exists()) {
                continue;
            }
            String desc = describe(resource);
            try {
                ToolDefinition def = parse(resource);
                if (!seen.add(def.getId())) {
                    throw new ToolValidationException(
                            "ToolDefinitionJsonLoader: duplicate tool id=" + def.getId()
                                    + " from " + desc);
                }
                out.add(def);
                log.info("ToolDefinitionJsonLoader: loaded id={} handlerClass={} from {}",
                        def.getId(), def.getHandlerClass(), desc);
            } catch (ToolValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new ToolValidationException(
                        "ToolDefinitionJsonLoader: failed to load " + desc, e);
            }
        }
        return out;
    }

    static ToolDefinition parse(Resource resource) throws Exception {
        JsonNode root;
        InputStream in = resource.getInputStream();
        try {
            root = MAPPER.readTree(in);
        } finally {
            in.close();
        }
        if (root == null || !root.isObject()) {
            throw new ToolValidationException(
                    "ToolDefinitionJsonLoader: root must be a JSON object: " + describe(resource));
        }
        String id = textOrNull(root.get("id"));
        if (!StringUtils.hasText(id)) {
            throw new ToolValidationException(
                    "ToolDefinitionJsonLoader: id required: " + describe(resource));
        }
        String handlerClass = textOrNull(root.get("handlerClass"));
        if (!StringUtils.hasText(handlerClass)) {
            throw new ToolValidationException(
                    "ToolDefinitionJsonLoader: handlerClass required: " + describe(resource));
        }
        JsonNode schemaNode = root.get("schema");
        JsonNode parametersNode = root.get("parameters");
        boolean hasSchema = schemaNode != null && !schemaNode.isNull() && schemaNode.isObject();
        boolean hasParameters = parametersNode != null && !parametersNode.isNull() && parametersNode.isObject();
        if (!hasSchema && !hasParameters) {
            throw new ToolValidationException(
                    "ToolDefinitionJsonLoader: schema (or parameters) required: " + describe(resource));
        }
        JsonNode effectiveSchema = hasSchema ? schemaNode : null;
        return ToolDefinition.builder()
                .id(id)
                .description(textOrNull(root.get("description")))
                .text(textOrNull(root.get("text")))
                .handlerClass(handlerClass)
                .schema(parseSchema(effectiveSchema, parametersNode, id.trim(),
                        textOrNull(root.get("description"))))
                .build();
    }

    private static ToolSchema parseSchema(JsonNode schemaNode, JsonNode topLevelParameters,
                                          String id, String fallbackDescription) {
        if (schemaNode == null || schemaNode.isNull() || !schemaNode.isObject()) {
            return ToolSchema.builder()
                    .name(id)
                    .description(fallbackDescription)
                    .parametersSchema(topLevelParameters)
                    .build();
        }
        String name = textOrNull(schemaNode.get("name"));
        if (!StringUtils.hasText(name)) {
            name = id;
        }
        String description = textOrNull(schemaNode.get("description"));
        if (!StringUtils.hasText(description)) {
            description = fallbackDescription;
        }
        JsonNode parameters = schemaNode.get("parameters");
        if (parameters != null && parameters.isNull()) {
            parameters = null;
        }
        return ToolSchema.builder()
                .name(name.trim())
                .description(description)
                .parametersSchema(parameters)
                .build();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return node.asText(null);
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text : null;
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
        return StringUtils.hasText(filename) ? filename : String.valueOf(resource);
    }
}
