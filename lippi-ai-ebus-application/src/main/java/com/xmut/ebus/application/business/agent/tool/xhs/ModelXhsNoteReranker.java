package com.xmut.ebus.application.business.agent.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * pi-ai {@link XhsNoteReranker}: ranks pool ids by intent; never puts URLs in the prompt.
 */
public final class ModelXhsNoteReranker implements XhsNoteReranker {

    private static final Logger log = LoggerFactory.getLogger(ModelXhsNoteReranker.class);

    private static final String SYSTEM = "你是小红书笔记排序助手。按用户意图对候选笔记重排。"
            + "只输出 JSON 对象 {\"ids\":[\"h1\",...]} 或纯 JSON 字符串数组。"
            + "只能使用给定 id，不要改写标题，不要输出链接。";

    private final ModelProvider modelProvider;
    private final XhsNoteSearchProperties properties;
    private final ObjectMapper objectMapper;

    public ModelXhsNoteReranker(ModelProvider modelProvider, XhsNoteSearchProperties properties) {
        this(modelProvider, properties, new ObjectMapper());
    }

    ModelXhsNoteReranker(ModelProvider modelProvider, XhsNoteSearchProperties properties, ObjectMapper objectMapper) {
        this.modelProvider = modelProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> orderIds(String intent, List<XhsNoteCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> allowed = poolIds(pool);
        try {
            String payload = writePayload(intent, pool);
            ModelRequest request = ModelRequest.builder()
                    .useCase(properties.getSearcher().getRerankUseCase())
                    .messages(Arrays.asList(Message.system(SYSTEM), Message.user(payload)))
                    .temperature(0.0)
                    .maxTokens(512)
                    .build();
            ModelResponse response = modelProvider.complete(request);
            String content = response != null ? response.getContent() : null;
            return parseIds(content, allowed);
        } catch (Exception ex) {
            log.warn("xhs note model rerank failed: {}", ex.toString());
            return Collections.emptyList();
        }
    }

    private String writePayload(String intent, List<XhsNoteCandidate> pool) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("intent", intent != null ? intent : "");
        ArrayNode candidates = root.putArray("candidates");
        for (XhsNoteCandidate c : pool) {
            if (c == null) {
                continue;
            }
            ObjectNode node = candidates.addObject();
            node.put("id", emptyIfNull(c.getId()));
            node.put("title", emptyIfNull(c.getTitle()));
            node.put("desc", emptyIfNull(c.getDesc()));
            node.put("likedCount", emptyIfNull(c.getLikedCount()));
            node.put("author", emptyIfNull(c.getAuthor()));
        }
        return objectMapper.writeValueAsString(root);
    }

    private List<String> parseIds(String raw, Set<String> allowed) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        String json = stripFence(raw);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("xhs note rerank parse failed: {}", ex.toString());
            return Collections.emptyList();
        }
        JsonNode array = root;
        if (root != null && root.isObject() && root.has("ids")) {
            array = root.get("ids");
        }
        if (array == null || !array.isArray()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (JsonNode item : array) {
            if (item == null || !item.isTextual()) {
                continue;
            }
            String id = item.asText();
            if (!allowed.contains(id) || seen.contains(id)) {
                continue;
            }
            ids.add(id);
            seen.add(id);
        }
        return ids;
    }

    private static Set<String> poolIds(List<XhsNoteCandidate> pool) {
        Set<String> ids = new HashSet<String>();
        for (XhsNoteCandidate c : pool) {
            if (c != null && c.getId() != null) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    private static String stripFence(String raw) {
        String s = raw.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNl = s.indexOf('\n');
        if (firstNl < 0) {
            return s;
        }
        s = s.substring(firstNl + 1);
        int fence = s.lastIndexOf("```");
        if (fence >= 0) {
            s = s.substring(0, fence);
        }
        return s.trim();
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
