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
 * 从本轮工作区读 output 相对路径上的 JSON。
 * 功能描述：校验路径落在工作区里，把该对象收成 {@link ParsedGenerationOutput}。
 * 关键设计：Skill 只声明 output（通常 view.json）。同目录 artifact.json 是 render_view
 * 的中间领域实体，落库 data 用它；没有该文件时 data 与 view 同一份。
 * 文件缺失或空白抛 {@code output file missing: } 加相对路径；含 {@code ..} 拒绝。
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

    public ParsedGenerationOutput read(Path root, String outputRel) {
        if (root == null) {
            throw new IllegalArgumentException("workspace required");
        }
        rejectEscape(outputRel);
        Path file = LocalFileSupport.resolveUnder(root, outputRel);
        Map<String, Object> view = viewMap(readObject(file, outputRel), outputRel);
        return new ParsedGenerationOutput(view, dataBeside(file, outputRel, view));
    }

    private Map<String, Object> dataBeside(Path outputFile, String outputRel, Map<String, Object> view) {
        Path dir = outputFile.getParent();
        if (dir == null) {
            return view;
        }
        Path sibling = dir.resolve("artifact.json");
        if (!Files.isRegularFile(sibling)) {
            return view;
        }
        return objectMapper.convertValue(readObject(sibling, siblingRel(outputRel)), MAP_TYPE);
    }

    private static String siblingRel(String outputRel) {
        if (!StringUtils.hasText(outputRel)) {
            return "artifact.json";
        }
        int slash = outputRel.lastIndexOf('/');
        if (slash < 0) {
            return "artifact.json";
        }
        return outputRel.substring(0, slash + 1) + "artifact.json";
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
