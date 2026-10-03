package com.xmut.forma.application.business.agent.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin dual-track parse of model final text for history echo and persist.
 * <p>
 * Business payload rules (no picklist field validation):
 * <ul>
 *   <li>Non-JSON text → {@code {text: trimmed}}</li>
 *   <li>Root has {@code artifact} key: object → payload = artifact map; else → empty map</li>
 *   <li>Else root has no {@code view} → payload = entire root object</li>
 *   <li>Else root has only {@code view} → payload = empty map</li>
 *   <li>Else (view + other keys, no artifact) → payload = root minus {@code view}</li>
 * </ul>
 * Skill {@code rawView} is taken from root {@code view} whenever that value is an object.
 * If {@code artifact} key exists but is not an object, {@code businessPayload} is empty and
 * {@code view} is still extracted when present.
 */
@Component
public class GenerationOutputParser {

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final ObjectMapper objectMapper;

    public GenerationOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedGenerationOutput parse(String finalResponse) {
        return parseEnvelope(finalResponse, null, false);
    }

    /**
     * Dual-track parse; if the root JSON has a textual {@code output} pointer,
     * only the workspace file is trusted (root view/artifact ignored).
     * File content is parsed without following a nested {@code output} pointer.
     */
    public ParsedGenerationOutput parse(String finalResponse, Path runWorkspaceRoot) {
        return parseEnvelope(finalResponse, runWorkspaceRoot, true);
    }

    private ParsedGenerationOutput parseEnvelope(String finalResponse, Path runWorkspaceRoot,
            boolean allowPointer) {
        if (finalResponse == null) {
            return textPayload("");
        }
        String trimmed = finalResponse.trim();
        if (!StringUtils.hasText(trimmed)) {
            return textPayload(trimmed);
        }

        for (String jsonCandidate : jsonCandidates(trimmed)) {
            if (!StringUtils.hasText(jsonCandidate)) {
                continue;
            }
            JsonNode root;
            try {
                root = objectMapper.readTree(jsonCandidate);
            } catch (Exception ex) {
                continue;
            }
            if (root == null || !root.isObject()) {
                continue;
            }

            if (allowPointer) {
                ParsedGenerationOutput fromPointer = tryResolveOutputPointer(root, runWorkspaceRoot);
                if (fromPointer != null) {
                    return fromPointer;
                }
            }

            JsonNode viewNode = root.get("view");
            if (viewNode != null && !viewNode.isObject() && !root.has("artifact")) {
                // 有 view 但不是对象，继续试下一个候选
                continue;
            }

            JsonNode artifactNode = root.get("artifact");
            boolean artifactObject = artifactNode != null && artifactNode.isObject();

            Map<String, Object> rawView = null;
            if (viewNode != null && viewNode.isObject()) {
                rawView = objectMapper.convertValue(viewNode, MAP_TYPE);
            }

            Map<String, Object> businessPayload = resolveBusinessPayload(root, artifactObject, artifactNode);
            return new ParsedGenerationOutput(rawView, businessPayload);
        }

        return textPayload(trimmed);
    }

    /**
     * @return parsed file envelope, or {@code null} when root has no textual output pointer
     */
    private ParsedGenerationOutput tryResolveOutputPointer(JsonNode root, Path runWorkspaceRoot) {
        JsonNode outputNode = root.get("output");
        if (outputNode == null || !outputNode.isTextual() || !StringUtils.hasText(outputNode.asText())) {
            return null;
        }
        if (runWorkspaceRoot == null) {
            throw new IllegalArgumentException("workspace required for output pointer");
        }
        Path file;
        try {
            file = LocalFileSupport.resolveUnder(runWorkspaceRoot, outputNode.asText().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid output path");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("output file missing: " + outputNode.asText());
        }
        String fileText;
        try {
            byte[] bytes = Files.readAllBytes(file);
            fileText = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid output path");
        }
        JsonNode fileRoot;
        try {
            fileRoot = objectMapper.readTree(fileText);
        } catch (Exception ex) {
            return parseEnvelope(fileText, runWorkspaceRoot, false);
        }
        if (isViewV2Document(fileRoot)) {
            Map<String, Object> view = objectMapper.convertValue(fileRoot, MAP_TYPE);
            Path artifactFile = file.getParent().resolve("artifact.json");
            if (!Files.isRegularFile(artifactFile)) {
                throw new IllegalArgumentException("artifact file missing: " + artifactFile.getFileName());
            }
            Map<String, Object> artifact;
            try {
                artifact = objectMapper.readValue(Files.readAllBytes(artifactFile), MAP_TYPE);
            } catch (IOException ex) {
                throw new IllegalArgumentException("invalid output path");
            }
            return new ParsedGenerationOutput(view, artifact);
        }
        return parseEnvelope(fileText, runWorkspaceRoot, false);
    }

    static boolean isViewV2Document(JsonNode n) {
        if (n == null || !n.isObject()) {
            return false;
        }
        if (n.path("version").asInt(0) != 2) {
            return false;
        }
        if (!n.path("format").isTextual()) {
            return false;
        }
        if (!n.path("content").isTextual()) {
            return false;
        }
        return true;
    }

    private Map<String, Object> resolveBusinessPayload(JsonNode root, boolean artifactObject, JsonNode artifactNode) {
        if (root.has("artifact")) {
            if (artifactObject) {
                Map<String, Object> artifactMap = objectMapper.convertValue(artifactNode, MAP_TYPE);
                return artifactMap == null ? Collections.<String, Object>emptyMap() : artifactMap;
            }
            return Collections.<String, Object>emptyMap();
        }
        if (!root.has("view")) {
            return objectMapper.convertValue(root, MAP_TYPE);
        }
        if (root.size() == 1) {
            return Collections.<String, Object>emptyMap();
        }
        Map<String, Object> withoutView = new HashMap<String, Object>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("view".equals(entry.getKey())) {
                continue;
            }
            withoutView.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
        }
        return withoutView;
    }

    private static ParsedGenerationOutput textPayload(String text) {
        return new ParsedGenerationOutput(null, Collections.singletonMap("text", text));
    }

    /**
     * 候选顺序：从后往前的 fenced JSON，再尝试整段首尾花括号。
     * 避免「中间碎 fence / 截断 fence」抢先导致整段解析失败。
     */
    static java.util.List<String> jsonCandidates(String raw) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        Matcher matcher = FENCED_JSON.matcher(raw);
        java.util.ArrayList<String> fences = new java.util.ArrayList<String>();
        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            if (StringUtils.hasText(inner)) {
                fences.add(inner);
            }
        }
        for (int i = fences.size() - 1; i >= 0; i--) {
            out.add(fences.get(i));
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String braced = raw.substring(start, end + 1);
            if (!out.contains(braced)) {
                out.add(braced);
            }
        }
        return out;
    }
}
