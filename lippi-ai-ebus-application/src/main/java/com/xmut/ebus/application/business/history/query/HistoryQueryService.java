package com.xmut.ebus.application.business.history.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin;
import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.dto.HistoryArtifactSummaryDTO;
import com.xmut.ebus.application.business.history.support.HistoryViewResignSupport;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HistoryQuery：只读聚合本人近 60 天 picklist/sku 成果。
 */
@Service
@RequiredArgsConstructor
public class HistoryQueryService {

    public static final int HISTORY_WINDOW_DAYS = 60;
    public static final String MSG_UNAVAILABLE = "成果不存在或无权查看";

    private static final List<ArtifactType> HISTORY_TYPES = Collections.unmodifiableList(
            Arrays.asList(ArtifactType.PICKLIST, ArtifactType.SKU));

    private final ArtifactRepository artifactRepository;
    private final GenerationRunRepository generationRunRepository;
    private final HistoryViewResignSupport historyViewResignSupport;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<HistoryArtifactSummaryDTO> list(String userId, String sceneCodeOrNull) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sceneCode = StringUtils.hasText(sceneCodeOrNull) ? sceneCodeOrNull.trim() : null;
        Instant since = Instant.now(clock).minus(HISTORY_WINDOW_DAYS, ChronoUnit.DAYS);
        List<Artifact> rows = artifactRepository.listByUserSince(uid, since, HISTORY_TYPES, sceneCode);
        List<HistoryArtifactSummaryDTO> result = new ArrayList<HistoryArtifactSummaryDTO>(rows.size());
        for (Artifact artifact : rows) {
            result.add(toSummary(artifact));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public HistoryArtifactDetailDTO findById(String userId, String artifactId) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String id = StringUtils.requireHasText(artifactId, "artifactId required");
        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
        if (!uid.equals(artifact.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }
        if (!isHistoryType(artifact.getType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }
        Instant since = Instant.now(clock).minus(HISTORY_WINDOW_DAYS, ChronoUnit.DAYS);
        if (artifact.getCreatedAt() == null || artifact.getCreatedAt().isBefore(since)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }
        Map<String, Object> view = extractAndResignView(artifact.getPayloadJson(), artifact.getUserId());
        return new HistoryArtifactDetailDTO(
                artifact.getId(),
                artifact.getType().getCode(),
                artifact.getSceneCode(),
                artifact.getTitle(),
                artifact.getCreatedAt(),
                view,
                resolveSessionId(artifact.getRunId()));
    }

    private String resolveSessionId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        return generationRunRepository.findById(runId)
                .map(GenerationRun::getSessionId)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private Map<String, Object> extractAndResignView(String payloadJson, String ownerUserId) {
        if (!StringUtils.hasText(payloadJson)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    payloadJson, new TypeReference<Map<String, Object>>() {
                    });
            Object viewObj = payload.get(ArtifactPersistPlugin.PAYLOAD_VIEW);
            if (!(viewObj instanceof Map)) {
                return Collections.emptyMap();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> view = (Map<String, Object>) viewObj;
            return historyViewResignSupport.resignView(view, ownerUserId);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static boolean isHistoryType(ArtifactType type) {
        return type == ArtifactType.PICKLIST || type == ArtifactType.SKU;
    }

    private static HistoryArtifactSummaryDTO toSummary(Artifact artifact) {
        return new HistoryArtifactSummaryDTO(
                artifact.getId(),
                artifact.getType().getCode(),
                artifact.getSceneCode(),
                artifact.getTitle(),
                artifact.getCreatedAt());
    }
}
