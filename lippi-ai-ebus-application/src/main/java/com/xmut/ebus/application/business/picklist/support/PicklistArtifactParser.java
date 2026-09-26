package com.xmut.ebus.application.business.picklist.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistItemCommand;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.picklist.constant.PicklistDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型终态文本解析选品 JSON；结构不合格抛业务异常（不可用成果）。
 */
@Component
public class PicklistArtifactParser {

    public static final String MSG_UNUSABLE = "选品成果不合格，请重试";

    /** Title prefix for priority trial items; shared with {@link PicklistViewProjector}. */
    public static final String PRIORITY_MARK = "【优先试】";

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public PicklistArtifactParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param rawText 模型 finalResponse
     * @param userId  操作者
     * @param runId   当前 GenerationRun
     */
    public PersistPicklistCommand parse(String rawText, String userId, String runId) {
        StringUtils.requireHasText(userId, "用户 ID 不能为空");
        StringUtils.requireHasText(runId, "Run ID 不能为空");
        if (!StringUtils.hasText(rawText)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        String json = extractJson(rawText.trim());
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        String templateId = resolveTemplateId(root);
        String disclaimer = requiredDisclaimer(root);
        String assumptions = optionalText(root, "assumptions");
        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        int size = itemsNode.size();
        if (size < PicklistDefaults.MIN_ITEMS || size > PicklistDefaults.MAX_ITEMS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        List<PersistPicklistItemCommand> items = new ArrayList<PersistPicklistItemCommand>(size);
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
            }
            items.add(PersistPicklistItemCommand.builder()
                    .title(requiredText(itemNode, "title"))
                    .priceBand(requiredText(itemNode, "priceBand"))
                    .reason(requiredText(itemNode, "reason"))
                    .differentiation(requiredText(itemNode, "differentiation"))
                    .demand(requiredText(itemNode, "demand"))
                    .competition(requiredText(itemNode, "competition"))
                    .margin(requiredText(itemNode, "margin"))
                    .risk(requiredText(itemNode, "risk"))
                    .build());
        }

        return PersistPicklistCommand.builder()
                .userId(userId)
                .runId(runId)
                .templateId(templateId)
                .disclaimer(disclaimer)
                .assumptions(assumptions)
                .items(items)
                .build();
    }

    private static String resolveTemplateId(JsonNode root) {
        JsonNode value = root.get("templateId");
        if (value == null || value.isNull() || !value.isValueNode() || !StringUtils.hasText(value.asText())) {
            return PicklistDefaults.TEMPLATE_ID;
        }
        String templateId = value.asText().trim();
        if (!PicklistDefaults.TEMPLATE_ID.equals(templateId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return templateId;
    }

    private static String requiredDisclaimer(JsonNode root) {
        String disclaimer = requiredText(root, "disclaimer");
        if (!disclaimer.contains("非实时")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return disclaimer;
    }

    private static String extractJson(String raw) {
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
        throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return text.trim();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
