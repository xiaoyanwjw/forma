package com.xmut.lims.pi.agent.graph.checkpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Checkpoint JSON 编解码器。
 * 功能描述：在存储与内存对象之间转换 Checkpoint。
 */
public final class CheckpointCodec {

    private static final Logger log = LoggerFactory.getLogger(CheckpointCodec.class);

    private final ObjectMapper mapper;

    public CheckpointCodec() {
        this(defaultMapper());
    }

    public CheckpointCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static ObjectMapper defaultMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    public String encode(Checkpoint checkpoint) throws JsonProcessingException {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint required");
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("checkpointId", checkpoint.getCheckpointId());
        dto.put("runId", checkpoint.getRunId());
        dto.put("step", checkpoint.getStep());
        dto.put("currentNode", checkpoint.getCurrentNode());
        dto.put("createdAt", checkpoint.getCreatedAt() != null
                ? checkpoint.getCreatedAt().toString() : Instant.now().toString());
        dto.put("metadata", checkpoint.getMetadata() != null
                ? checkpoint.getMetadata() : Collections.emptyMap());
        dto.put("state", encodeState(checkpoint.getState()));
        return mapper.writeValueAsString(dto);
    }

    public Optional<Checkpoint> decode(String json) {
        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> dto = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            GraphState state = decodeState(dto.get("state"));
            Instant createdAt = Instant.parse(String.valueOf(dto.get("createdAt")));
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = dto.get("metadata") instanceof Map
                    ? (Map<String, Object>) dto.get("metadata")
                    : Collections.emptyMap();
            Number step = (Number) dto.get("step");
            return Optional.of(new Checkpoint(
                    (String) dto.get("checkpointId"),
                    (String) dto.get("runId"),
                    step != null ? step.intValue() : 0,
                    (String) dto.get("currentNode"),
                    state,
                    createdAt,
                    metadata));
        } catch (Exception e) {
            log.warn("Failed to decode hermes checkpoint JSON: {}", e.toString());
            return Optional.empty();
        }
    }

    Map<String, Object> encodeState(GraphState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (state == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : state.getValues().entrySet()) {
            out.put(e.getKey(), encodeValue(e.getKey(), e.getValue()));
        }
        return out;
    }

    GraphState decodeState(Object raw) {
        if (!(raw instanceof Map)) {
            return GraphState.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            typed.put(e.getKey(), decodeValue(e.getKey(), e.getValue()));
        }
        return GraphState.create(typed);
    }

    private Object encodeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (StateKeys.MESSAGES.equals(key) && value instanceof List) {
            return encodeMessages((List<?>) value);
        }
        if (StateKeys.TOOL_CALLS.equals(key) && value instanceof List) {
            return encodeToolCalls((List<?>) value);
        }
        if (StateKeys.TOOL_RESULTS.equals(key) && value instanceof List) {
            return encodeToolResults((List<?>) value);
        }
        if (StateKeys.AVAILABLE_TOOLS.equals(key) && value instanceof List) {
            return encodeToolSchemas((List<?>) value);
        }
        if (StateKeys.TOOL_APPROVAL.equals(key)) {
            if (value instanceof ToolDecision) {
                return ((ToolDecision) value).name();
            }
            return String.valueOf(value);
        }
        if (value instanceof JsonNode) {
            return mapper.convertValue(value, Object.class);
        }
        return value;
    }

    private Object decodeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (StateKeys.MESSAGES.equals(key)) {
            return decodeMessages(value);
        }
        if (StateKeys.TOOL_CALLS.equals(key)) {
            return decodeToolCalls(value);
        }
        if (StateKeys.TOOL_RESULTS.equals(key)) {
            return decodeToolResults(value);
        }
        if (StateKeys.AVAILABLE_TOOLS.equals(key)) {
            return decodeToolSchemas(value);
        }
        if (StateKeys.TOOL_APPROVAL.equals(key)) {
            if (value instanceof ToolDecision) {
                return value;
            }
            try {
                return ToolDecision.valueOf(String.valueOf(value).trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid TOOL_APPROVAL value (typed decode fail-closed): " + value, ex);
            }
        }
        return value;
    }

    private List<Map<String, Object>> encodeMessages(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Message)) {
                continue;
            }
            Message m = (Message) o;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", m.getRole());
            row.put("content", m.getContent());
            row.put("toolCallId", m.getToolCallId());
            row.put("toolCalls", encodeToolCalls(
                    m.getToolCalls() != null ? m.getToolCalls() : Collections.emptyList()));
            if (m.hasParts()) {
                row.put("parts", encodeParts(m.getParts()));
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> encodeParts(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof ContentPart)) {
                continue;
            }
            ContentPart p = (ContentPart) o;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", p.getType());
            row.put("text", p.getText());
            row.put("url", p.getUrl());
            row.put("detail", p.getDetail());
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ContentPart> decodeParts(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<ContentPart> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            out.add(ContentPart.builder()
                    .type((String) row.get("type"))
                    .text((String) row.get("text"))
                    .url((String) row.get("url"))
                    .detail((String) row.get("detail"))
                    .build());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Message> decodeMessages(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Message> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            Message.MessageBuilder builder = Message.builder()
                    .role((String) row.get("role"))
                    .content((String) row.get("content"))
                    .toolCallId((String) row.get("toolCallId"))
                    .toolCalls(decodeToolCalls(row.get("toolCalls")));
            if (row.containsKey("parts")) {
                builder.parts(decodeParts(row.get("parts")));
            }
            out.add(builder.build());
        }
        return out;
    }

    private List<Map<String, Object>> encodeToolCalls(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof ToolCallEntry)) {
                continue;
            }
            ToolCallEntry c = (ToolCallEntry) o;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("toolName", c.getToolName());
            row.put("arguments", c.getArguments() != null
                    ? mapper.convertValue(c.getArguments(), Object.class) : null);
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ToolCallEntry> decodeToolCalls(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<ToolCallEntry> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            JsonNode args = row.get("arguments") != null
                    ? mapper.valueToTree(row.get("arguments"))
                    : null;
            out.add(new ToolCallEntry(
                    (String) row.get("id"),
                    (String) row.get("toolName"),
                    args));
        }
        return out;
    }

    private List<Map<String, Object>> encodeToolResults(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof ToolResult)) {
                continue;
            }
            ToolResult r = (ToolResult) o;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("callId", r.getCallId());
            row.put("toolName", r.getToolName());
            row.put("success", r.isSuccess());
            row.put("output", r.getOutput());
            row.put("errorMessage", r.getErrorMessage());
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ToolResult> decodeToolResults(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<ToolResult> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            boolean success = Boolean.TRUE.equals(row.get("success"));
            String callId = (String) row.get("callId");
            String toolName = (String) row.get("toolName");
            if (success) {
                out.add(ToolResult.ok(callId, toolName, (String) row.get("output")));
            } else {
                out.add(ToolResult.failed(callId, toolName, (String) row.get("errorMessage")));
            }
        }
        return out;
    }

    private List<Map<String, Object>> encodeToolSchemas(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof ToolSchema)) {
                continue;
            }
            ToolSchema s = (ToolSchema) o;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", s.getName());
            row.put("description", s.getDescription());
            row.put("parametersSchema", s.getParametersSchema() != null
                    ? mapper.convertValue(s.getParametersSchema(), Object.class) : null);
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ToolSchema> decodeToolSchemas(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<ToolSchema> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            JsonNode params = row.get("parametersSchema") != null
                    ? mapper.valueToTree(row.get("parametersSchema"))
                    : null;
            out.add(ToolSchema.builder()
                    .name((String) row.get("name"))
                    .description((String) row.get("description"))
                    .parametersSchema(params)
                    .build());
        }
        return out;
    }
}
