package com.xmut.forma.extension.output;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 从本轮工作区读 view 与 artifact 两个相对路径。
 * 功能描述：校验路径落在工作区里，把两个 JSON 对象收成 {@link ParsedGenerationOutput}。
 * 关键设计：文件缺失或空白抛 {@code output file missing: } 加相对路径；含 {@code ..} 拒绝。
 */
public final class WorkspaceOutputReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final ObjectMapper objectMapper;

    public WorkspaceOutputReader() {
        this(new ObjectMapper());
    }

    WorkspaceOutputReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedGenerationOutput read(Path root, String viewRel, String artifactRel) {
        if (root == null) {
            throw new IllegalArgumentException("workspace required");
        }
        rejectEscape(viewRel);
        rejectEscape(artifactRel);
        Path viewFile = LocalFileSupport.resolveUnder(root, viewRel);
        Path artifactFile = LocalFileSupport.resolveUnder(root, artifactRel);
        Map<String, Object> rawView = viewMap(readObject(viewFile, viewRel), viewRel);
        Map<String, Object> artifact = objectMapper.convertValue(readObject(artifactFile, artifactRel), MAP_TYPE);
        return new ParsedGenerationOutput(rawView, artifact);
    }

    private Map<String, Object> viewMap(JsonNode node, String rel) {
        if (node.path("version").asInt(0) == 2 && !isViewV2Document(node)) {
            throw new IllegalArgumentException("invalid output file: " + rel);
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    /**
     * Computer view v2：version=2，且 format、content 都是文本。
     */
    static boolean isViewV2Document(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (node.path("version").asInt(0) != 2) {
            return false;
        }
        if (!node.path("format").isTextual()) {
            return false;
        }
        return node.path("content").isTextual();
    }

    private JsonNode readObject(Path file, String rel) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("output file missing: " + rel);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException("output file missing: " + rel);
        }
        if (bytes.length == 0 || !StringUtils.hasText(new String(bytes, StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("output file missing: " + rel);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid output file: " + rel);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("invalid output file: " + rel);
        }
        return node;
    }

    private static void rejectEscape(String relative) {
        if (relative != null && relative.contains("..")) {
            throw new IllegalArgumentException("path escapes run directory");
        }
    }
}
