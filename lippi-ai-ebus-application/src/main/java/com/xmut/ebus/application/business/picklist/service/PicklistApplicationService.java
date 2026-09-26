package com.xmut.ebus.application.business.picklist.service;

import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistItemCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.ObjectUtils;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.picklist.constant.PicklistDefaults;
import com.xmut.ebus.domain.business.picklist.model.Picklist;
import com.xmut.ebus.domain.business.picklist.model.PicklistItem;
import com.xmut.ebus.domain.business.picklist.repository.PicklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 选品成果写用例：校验可用成果后落库（不计费；结算由 Agent 编排）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PicklistApplicationService {

    public static final String MSG_UNUSABLE = "选品成果不合格，请重试";

    private final PicklistRepository picklistRepository;
    private final Clock clock;

    /**
     * 校验条数/字段后持久化，返回成果 DTO。
     */
    @Transactional(rollbackFor = Exception.class)
    public PicklistArtifactDTO persistUsable(PersistPicklistCommand command) {
        ObjectUtils.requireNonNull(command, "选品命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");
        String runId = StringUtils.requireHasText(command.getRunId(), "Run ID 不能为空");
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
        String picklistId = UUID.randomUUID().toString();
        List<PicklistItem> items = new ArrayList<PicklistItem>(rawItems.size());
        for (int i = 0; i < rawItems.size(); i++) {
            PersistPicklistItemCommand raw = rawItems.get(i);
            ObjectUtils.requireNonNull(raw, MSG_UNUSABLE);
            items.add(PicklistItem.of(
                    UUID.randomUUID().toString(),
                    picklistId,
                    i,
                    requireMaxLen(requireItemField(raw.getTitle()), PicklistDefaults.MAX_TITLE),
                    requireMaxLen(requireItemField(raw.getPriceBand()), PicklistDefaults.MAX_PRICE_BAND),
                    requireMaxLen(requireItemField(raw.getReason()), PicklistDefaults.MAX_REASON),
                    requireMaxLen(requireItemField(raw.getDifferentiation()), PicklistDefaults.MAX_DIM),
                    requireMaxLen(requireItemField(raw.getDemand()), PicklistDefaults.MAX_DIM),
                    requireMaxLen(requireItemField(raw.getCompetition()), PicklistDefaults.MAX_DIM),
                    requireMaxLen(requireItemField(raw.getMargin()), PicklistDefaults.MAX_DIM),
                    requireMaxLen(requireItemField(raw.getRisk()), PicklistDefaults.MAX_DIM),
                    now));
        }

        Picklist picklist = Picklist.create(
                picklistId,
                userId,
                runId,
                templateId,
                disclaimer,
                assumptions,
                items,
                now);
        picklistRepository.save(picklist);

        LoggerUtils.success(log, PicklistApplicationService.class, "persistUsable",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("picklistId", picklistId),
                NameValue.create("itemCount", items.size()));

        List<PicklistArtifactDTO.PicklistItemDTO> dtoItems =
                new ArrayList<PicklistArtifactDTO.PicklistItemDTO>(items.size());
        for (PicklistItem item : items) {
            dtoItems.add(PicklistArtifactDTO.PicklistItemDTO.from(item));
        }
        return new PicklistArtifactDTO(
                picklistId,
                runId,
                templateId,
                picklist.getDisclaimer(),
                picklist.getAssumptions(),
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
