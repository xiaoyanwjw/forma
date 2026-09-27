package com.xmut.ebus.application.business.agent.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.common.util.StringUtils;
import org.springframework.stereotype.Component;

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
 *   <li>Root has {@code artifact} object → payload = artifact map</li>
 *   <li>Else root has no {@code view} → payload = entire root object</li>
 *   <li>Else root has only {@code view} → payload = empty map</li>
 *   <li>Else (view + other keys, no artifact) → payload = root minus {@code view}</li>
 * </ul>
 * Skill {@code rawView} is taken from root {@code view} only when dual-track envelope
 * ({@code artifact} object) is present.
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
        if (finalResponse == null) {
            return textPayload("");
        }
        String trimmed = finalResponse.trim();
        if (!StringUtils.hasText(trimmed)) {
            return textPayload(trimmed);
        }

        String jsonCandidate = tryExtractJson(trimmed);
        if (jsonCandidate == null) {
            return textPayload(trimmed);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonCandidate);
        } catch (Exception ex) {
            return textPayload(trimmed);
        }
        if (root == null || !root.isObject()) {
            return textPayload(trimmed);
        }

        JsonNode artifactNode = root.get("artifact");
        boolean dualTrack = artifactNode != null && artifactNode.isObject();

        Map<String, Object> rawView = null;
        if (dualTrack) {
            JsonNode viewNode = root.get("view");
            if (viewNode != null && viewNode.isObject()) {
                rawView = objectMapper.convertValue(viewNode, MAP_TYPE);
            }
        }

        Map<String, Object> businessPayload = resolveBusinessPayload(root, dualTrack, artifactNode);
        return new ParsedGenerationOutput(rawView, businessPayload);
    }

    private Map<String, Object> resolveBusinessPayload(JsonNode root, boolean dualTrack, JsonNode artifactNode) {
        if (dualTrack) {
            Map<String, Object> artifactMap = objectMapper.convertValue(artifactNode, MAP_TYPE);
            return artifactMap == null ? Collections.<String, Object>emptyMap() : artifactMap;
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

    private static String tryExtractJson(String raw) {
        Matcher matcher = FENCED_JSON.matcher(raw);
        if (matcher.find()) {
            String inner = matcher.group(1).trim();
            if (StringUtils.hasText(inner)) {
                return inner;
            }
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }
}
