package com.xmut.ebus.application.business.picklist.support;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型终态文本解析选品 JSON；支持双轨信封 {@code {view, artifact}} 与扁平业务 JSON。
 * 结构不合格抛业务异常（不可用成果）。
 */
@Component
public class PicklistArtifactParser {

    public static final String MSG_UNUSABLE = "选品成果不合格，请重试";
    public static final String PRIORITY_MARK = "【优先试】";

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    /** 四维须以 高|中|低 + 全角/半角竖线或冒号 开头 */
    private static final Pattern LEVEL_PREFIX = Pattern.compile("^[高中低][｜|：:]");

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final ObjectMapper objectMapper;

    public PicklistArtifactParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param rawText 模型 finalResponse
     * @param userId  操作者
     * @param runId   当前 GenerationRun
     */
    public PicklistParseResult parse(String rawText, String userId, String runId) {
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

        JsonNode businessRoot = resolveBusinessRoot(root);
        Map<String, Object> rawView = extractRawView(root, businessRoot);

        String templateId = resolveTemplateId(businessRoot);
        String disclaimer = requiredDisclaimer(businessRoot);
        String assumptions = optionalText(businessRoot, "assumptions");
        JsonNode itemsNode = businessRoot.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        int size = itemsNode.size();
        if (size < PicklistDefaults.MIN_ITEMS || size > PicklistDefaults.MAX_ITEMS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        List<PersistPicklistItemCommand> items = new ArrayList<PersistPicklistItemCommand>(size);
        List<String> niches = new ArrayList<String>();
        int priorityCount = 0;
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
            }
            String title = requiredText(itemNode, "title");
            String painPoint = requiredText(itemNode, "painPoint");
            String angle = requiredText(itemNode, "angle");
            String diff = requiredText(itemNode, "diff");
            String niche = requiredText(itemNode, "niche");
            String demand = requiredLevelField(itemNode, "demand");
            String competition = requiredLevelField(itemNode, "competition");
            String margin = requiredLevelField(itemNode, "margin");
            String risk = requiredLevelField(itemNode, "risk");
            String sourceUrl = requiredHttps(itemNode, "sourceUrl");
            if (title.startsWith(PRIORITY_MARK)) {
                priorityCount++;
            }
            niches.add(niche);
            items.add(PersistPicklistItemCommand.builder()
                    .title(title)
                    .priceBand(requiredText(itemNode, "priceBand"))
                    .painPoint(painPoint)
                    .angle(angle)
                    .diff(diff)
                    .niche(niche)
                    .demand(demand)
                    .competition(competition)
                    .margin(margin)
                    .risk(risk)
                    .sourceUrl(sourceUrl)
                    .build());
        }
        if (priorityCount < 1 || priorityCount > 2) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        if (distinctCount(niches) < 3) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        PersistPicklistCommand command = PersistPicklistCommand.builder()
                .userId(userId)
                .runId(runId)
                .templateId(templateId)
                .disclaimer(disclaimer)
                .assumptions(assumptions)
                .items(items)
                .build();
        return new PicklistParseResult(command, rawView);
    }

    /**
     * Dual-track: business fields live under {@code artifact}. Flat JSON keeps fields at root.
     */
    private static JsonNode resolveBusinessRoot(JsonNode root) {
        JsonNode artifact = root.get("artifact");
        if (artifact != null && artifact.isObject()) {
            return artifact;
        }
        return root;
    }

    /**
     * Only accept Skill {@code view} when dual-track envelope is used ({@code artifact} present),
     * so flat legacy payloads never confuse Computer with a stray top-level view.
     */
    private Map<String, Object> extractRawView(JsonNode root, JsonNode businessRoot) {
        if (businessRoot == root) {
            return null;
        }
        JsonNode viewNode = root.get("view");
        if (viewNode == null || viewNode.isNull() || !viewNode.isObject()) {
            return null;
        }
        try {
            return objectMapper.convertValue(viewNode, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
    }

    private static String requiredLevelField(JsonNode itemNode, String field) {
        String text = requiredText(itemNode, field);
        if (!LEVEL_PREFIX.matcher(text).find()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return text;
    }

    private static int distinctCount(List<String> values) {
        List<String> unique = new ArrayList<String>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (!unique.contains(value)) {
                unique.add(value);
            }
        }
        return unique.size();
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

    private static String requiredHttps(JsonNode node, String field) {
        String text = requiredText(node, field);
        if (!text.startsWith("https://")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return text;
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
