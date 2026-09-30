package com.xmut.ebus.application.business.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pi tool {@code ask_human}：校验并规范化 question/options，返回 {@link ToolResult#interrupt}。
 * ToolNode 先写入 tool 回执再挂起；resume 把人的选项追加为 user 消息后继续。
 */
public final class AskHumanToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(AskHumanToolHandler.class);

    public static final String TOOL_NAME = ToolPolicyExtension.ASK_HUMAN_TOOL;

    private final ObjectMapper objectMapper;

    public AskHumanToolHandler() {
        this(new ObjectMapper());
    }

    AskHumanToolHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            ParsedAsk parsed = parse(call);
            if (parsed == null || !StringUtils.hasText(parsed.getQuestion()) || parsed.getOptions().isEmpty()) {
                return ToolResult.failed(callId, TOOL_NAME, "ask_human requires question and options[{id,label}]");
            }
            return ToolResult.interrupt(callId, TOOL_NAME, writeEcho(parsed));
        } catch (Exception ex) {
            log.warn("ask_human failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "ask_human failed: " + ex.getMessage());
        }
    }

    /** 从模型 tool_call.arguments 解析。 */
    public static ParsedAsk parse(ToolCallEntry call) {
        return parseArgs(arguments(call));
    }

    /** 从 handler 规范化后的 output JSON 解析（SSE 优先用）。 */
    public static ParsedAsk parseOutput(String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(output.trim());
            return parseArgs(root);
        } catch (Exception ex) {
            return null;
        }
    }

    private static ParsedAsk parseArgs(JsonNode args) {
        if (args == null || args.isNull() || !args.isObject()) {
            return null;
        }
        String question = text(args.get("question"));
        boolean allowFreeText = true;
        if (args.has("allowFreeText") && !args.get("allowFreeText").isNull()) {
            allowFreeText = args.get("allowFreeText").asBoolean(true);
        }
        List<Map<String, String>> options = parseOptions(args.get("options"));
        if (!StringUtils.hasText(question) || options.isEmpty()) {
            return null;
        }
        return new ParsedAsk(question.trim(), options, allowFreeText);
    }

    private String writeEcho(ParsedAsk parsed) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("question", parsed.getQuestion());
        root.put("allowFreeText", parsed.isAllowFreeText());
        ArrayNode opts = root.putArray("options");
        for (Map<String, String> option : parsed.getOptions()) {
            ObjectNode node = opts.addObject();
            node.put("id", option.get("id"));
            node.put("label", option.get("label"));
        }
        return objectMapper.writeValueAsString(root);
    }

    static List<Map<String, String>> parseOptions(JsonNode optionsNode) {
        if (optionsNode == null || !optionsNode.isArray()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> options = new ArrayList<Map<String, String>>();
        for (JsonNode item : optionsNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = text(item.get("id"));
            String label = text(item.get("label"));
            if (!StringUtils.hasText(id) || !StringUtils.hasText(label)) {
                continue;
            }
            Map<String, String> option = new LinkedHashMap<String, String>();
            option.put("id", id.trim());
            option.put("label", label.trim());
            options.add(option);
        }
        return options;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }

    public static final class ParsedAsk {
        private final String question;
        private final List<Map<String, String>> options;
        private final boolean allowFreeText;

        ParsedAsk(String question, List<Map<String, String>> options, boolean allowFreeText) {
            this.question = question;
            this.options = Collections.unmodifiableList(new ArrayList<Map<String, String>>(options));
            this.allowFreeText = allowFreeText;
        }

        public String getQuestion() {
            return question;
        }

        public List<Map<String, String>> getOptions() {
            return options;
        }

        public boolean isAllowFreeText() {
            return allowFreeText;
        }
    }
}
