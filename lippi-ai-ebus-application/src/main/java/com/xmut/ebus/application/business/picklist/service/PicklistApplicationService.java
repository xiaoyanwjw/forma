package com.xmut.ebus.application.business.picklist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistItemCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.ObjectUtils;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.ebus.domain.business.picklist.constant.PicklistDefaults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 选品成果写用例：校验可用成果后写入 ArtifactStore（不计费；结算由 Agent 编排）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PicklistApplicationService {

    public static final String MSG_UNUSABLE = "选品成果不合格，请重试";

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 校验条数/字段后持久化，返回成果 DTO。
     */
    @Transactional(rollbackFor = Exception.class)
    public PicklistArtifactDTO persistUsable(PersistPicklistCommand command) {
        ObjectUtils.requireNonNull(command, "选品命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");
        String runId = StringUtils.requireHasText(command.getRunId(), "Run ID 不能为空");
        String sceneCode = StringUtils.requireHasText(command.getSceneCode(), "场景不能为空");
        String disclaimer = requireMaxLen(
                StringUtils.requireHasText(command.getDisclaimer(), MSG_UNUSABLE),
                PicklistDefaults.MAX_DISCLAIMER);
        if (!disclaimer.contains("非实时")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        String templateId = resolveTemplateId(command.getTemplateId());
        String assumptions = null;
        if (StringUtils.hasText(command.getAssumptions())) {
            assumptions = requireMaxLen(command.getAssumptions().trim(), PicklistDefaults.MAX_ASSUMPTIONS);
        }

        List<PersistPicklistItemCommand> rawItems = command.getItems();
        if (rawItems == null
                || rawItems.size() < PicklistDefaults.MIN_ITEMS
                || rawItems.size() > PicklistDefaults.MAX_ITEMS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        Instant now = Instant.now(clock);
        String artifactId = UUID.randomUUID().toString();
        List<PicklistArtifactDTO.PicklistItemDTO> dtoItems =
                new ArrayList<PicklistArtifactDTO.PicklistItemDTO>(rawItems.size());
        List<Map<String, Object>> itemMaps = new ArrayList<Map<String, Object>>(rawItems.size());
        for (PersistPicklistItemCommand raw : rawItems) {
            ObjectUtils.requireNonNull(raw, MSG_UNUSABLE);
            String title = requireMaxLen(requireItemField(raw.getTitle()), PicklistDefaults.MAX_TITLE);
            String priceBand = requireMaxLen(requireItemField(raw.getPriceBand()), PicklistDefaults.MAX_PRICE_BAND);
            String painPoint = requireMaxLen(requireItemField(raw.getPainPoint()), PicklistDefaults.MAX_REASON);
            String angle = requireMaxLen(requireItemField(raw.getAngle()), PicklistDefaults.MAX_REASON);
            String diff = requireMaxLen(requireItemField(raw.getDiff()), PicklistDefaults.MAX_REASON);
            String niche = requireMaxLen(requireItemField(raw.getNiche()), PicklistDefaults.MAX_DIM);
            String demand = requireMaxLen(requireItemField(raw.getDemand()), PicklistDefaults.MAX_DIM);
            String competition = requireMaxLen(requireItemField(raw.getCompetition()), PicklistDefaults.MAX_DIM);
            String margin = requireMaxLen(requireItemField(raw.getMargin()), PicklistDefaults.MAX_DIM);
            String risk = requireMaxLen(requireItemField(raw.getRisk()), PicklistDefaults.MAX_DIM);
            dtoItems.add(new PicklistArtifactDTO.PicklistItemDTO(
                    title, priceBand, painPoint, angle, diff, niche, demand, competition, margin, risk));
            Map<String, Object> itemMap = new LinkedHashMap<String, Object>();
            itemMap.put("title", title);
            itemMap.put("priceBand", priceBand);
            itemMap.put("painPoint", painPoint);
            itemMap.put("angle", angle);
            itemMap.put("diff", diff);
            itemMap.put("niche", niche);
            itemMap.put("demand", demand);
            itemMap.put("competition", competition);
            itemMap.put("margin", margin);
            itemMap.put("risk", risk);
            itemMaps.add(itemMap);
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("disclaimer", disclaimer);
        if (assumptions != null) {
            payload.put("assumptions", assumptions);
        }
        payload.put("items", itemMaps);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }

        Artifact artifact = Artifact.create(
                artifactId, userId, runId, ArtifactType.PICKLIST, sceneCode,
                templateId, "选品清单", json, now);
        artifactRepository.save(artifact);

        LoggerUtils.success(log, PicklistApplicationService.class, "persistUsable",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("picklistId", artifactId),
                NameValue.create("itemCount", dtoItems.size()));

        return new PicklistArtifactDTO(
                artifactId,
                runId,
                templateId,
                disclaimer,
                assumptions,
                dtoItems);
    }

    private static String resolveTemplateId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return PicklistDefaults.TEMPLATE_ID;
        }
        String templateId = raw.trim();
        if (!PicklistDefaults.TEMPLATE_ID.equals(templateId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return templateId;
    }

    private static String requireItemField(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return value.trim();
    }

    private static String requireMaxLen(String value, int max) {
        if (value.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_UNUSABLE);
        }
        return value;
    }
}
