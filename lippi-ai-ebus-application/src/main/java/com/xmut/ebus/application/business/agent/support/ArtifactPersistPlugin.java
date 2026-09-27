package com.xmut.ebus.application.business.agent.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes projected Computer {@code view} plus business {@code data} to ArtifactStore.
 */
@Component
public class ArtifactPersistPlugin {

    public static final String PAYLOAD_VIEW = "view";
    public static final String PAYLOAD_DATA = "data";

    private static final int TITLE_MAX_LEN = 256;
    private static final String DEFAULT_TITLE = "成果";
    private static final String TEMPLATE_DOMESTIC_DEFAULT = "domestic-generic-default";
    public static final String MSG_SKU_UNUSABLE = "上架素材不合格：需含详情文案、展示说明与主图位（mediaObjectId）";
    public static final String MSG_LISTING_PLAN_UNUSABLE =
            "策划分镜不合格：需含 templateId、成交方向、3～5 条分镜与详情大纲、标题草稿";

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ArtifactPersistPlugin(ArtifactRepository artifactRepository,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param persistAs {@link SkillRunProfile#getPersistAs()}; none → {@link ArtifactType#CHAT}
     */
    public PersistedGenerationArtifact persist(String userId,
                                               String runId,
                                               String sceneCode,
                                               String persistAs,
                                               Map<String, Object> projectedView,
                                               Map<String, Object> businessPayload) {
        ArtifactType type = resolveType(persistAs);
        Map<String, Object> data = businessPayload != null
                ? businessPayload
                : Collections.<String, Object>emptyMap();
        if (type == ArtifactType.LISTING_PLAN) {
            requireUsableListingPlanPayload(data);
        } else if (type == ArtifactType.SKU) {
            requireUsableSkuPayload(data);
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        Map<String, Object> view = projectedView != null ? projectedView : Collections.<String, Object>emptyMap();
        payload.put(PAYLOAD_VIEW, view);
        payload.put(PAYLOAD_DATA, data);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "成果载荷无法序列化");
        }
        String title = resolveTitle(view);
        Instant now = Instant.now(clock);
        Artifact artifact = Artifact.create(id, userId, runId, type, sceneCode,
                null, title, json, now);
        artifactRepository.save(artifact);
        return new PersistedGenerationArtifact(id, Collections.<String, Object>emptyMap());
    }

    static ArtifactType resolveType(String persistAs) {
        if (SkillRunProfile.PERSIST_NONE.equals(persistAs)) {
            return ArtifactType.CHAT;
        }
        if (SkillRunProfile.PERSIST_PICKLIST.equals(persistAs)) {
            return ArtifactType.PICKLIST;
        }
        if (SkillRunProfile.PERSIST_SKU.equals(persistAs)) {
            return ArtifactType.SKU;
        }
        if (SkillRunProfile.PERSIST_LISTING_PLAN.equals(persistAs)) {
            return ArtifactType.LISTING_PLAN;
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "未支持的成果类型: " + persistAs);
    }

    /**
     * 可用策划分镜：templateId 固定国内通用默认；driver、titleDraft；frames/modules 各 3～5 条非空短句。
     */
    static void requireUsableListingPlanPayload(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        String templateId = text(data.get("templateId"));
        if (!TEMPLATE_DOMESTIC_DEFAULT.equals(templateId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        if (!StringUtils.hasText(text(data.get("driver")))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        if (!StringUtils.hasText(text(data.get("titleDraft")))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        requireNonEmptyStringList(data.get("frames"), 3, 5);
        requireNonEmptyStringList(data.get("modules"), 3, 5);
    }

    /**
     * 可用 Listing：跨平台公共文案四字段 + ≥1 mediaObjectId；templateId 固定国内通用默认。
     */
    static void requireUsableSkuPayload(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SKU_UNUSABLE);
        }
        String templateId = text(data.get("templateId"));
        if (!TEMPLATE_DOMESTIC_DEFAULT.equals(templateId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SKU_UNUSABLE);
        }
        if (!StringUtils.hasText(text(data.get("detailTitle")))
                || !StringUtils.hasText(text(data.get("detailBody")))
                || !StringUtils.hasText(text(data.get("displayNotes")))
                || !StringUtils.hasText(text(data.get("heroPlan")))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SKU_UNUSABLE);
        }
        List<String> mediaIds = mediaObjectIds(data.get("mediaObjectIds"));
        if (mediaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SKU_UNUSABLE);
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> mediaObjectIds(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (Object item : (List<?>) raw) {
            String id = text(item);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static void requireNonEmptyStringList(Object raw, int minSize, int maxSize) {
        if (!(raw instanceof List)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        List<?> list = (List<?>) raw;
        if (list.size() < minSize || list.size() > maxSize) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
        }
        for (Object item : list) {
            if (!StringUtils.hasText(text(item))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_PLAN_UNUSABLE);
            }
        }
    }

    static String resolveTitle(Map<String, Object> projectedView) {
        if (projectedView == null || projectedView.isEmpty()) {
            return DEFAULT_TITLE;
        }
        Object titleObj = projectedView.get("title");
        if (titleObj != null && StringUtils.hasText(String.valueOf(titleObj))) {
            return truncateTitle(String.valueOf(titleObj).trim());
        }
        Object blocksObj = projectedView.get("blocks");
        if (blocksObj instanceof List) {
            for (Object blockObj : (List<?>) blocksObj) {
                if (!(blockObj instanceof Map)) {
                    continue;
                }
                Map<?, ?> block = (Map<?, ?>) blockObj;
                if (!"markdown".equals(block.get("type"))) {
                    continue;
                }
                Object text = block.get("text");
                if (text != null && StringUtils.hasText(String.valueOf(text))) {
                    return truncateTitle(String.valueOf(text).trim());
                }
            }
        }
        return DEFAULT_TITLE;
    }

    private static String truncateTitle(String raw) {
        if (raw.length() <= TITLE_MAX_LEN) {
            return raw;
        }
        return raw.substring(0, TITLE_MAX_LEN);
    }
}
