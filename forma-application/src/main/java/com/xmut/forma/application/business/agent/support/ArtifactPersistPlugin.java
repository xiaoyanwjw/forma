package com.xmut.forma.application.business.agent.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes projected Computer {@code view} plus business {@code data} to ArtifactStore.
 * <p>
 * Settle gate is projectable view (upstream); this plugin does not hard-validate
 * business payload by {@code persistAs}.
 */
@Component
public class ArtifactPersistPlugin {

    public static final String PAYLOAD_VIEW = "view";
    public static final String PAYLOAD_DATA = "data";

    private static final int TITLE_MAX_LEN = 256;
    private static final String DEFAULT_TITLE = "成果";

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
     * @param persistAs {@link SkillRunProfile#getPersistAs()}; none → chat
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
        // 同 Run 一果：uk_forma_artifact_run；策划→执行 / 补充改策划 覆盖写。
        Optional<Artifact> existing = artifactRepository.findByRunId(runId);
        if (existing.isPresent()) {
            Artifact artifact = existing.get();
            artifact.replaceContent(type, sceneCode, null, title, json, now);
            artifactRepository.update(artifact);
            return new PersistedGenerationArtifact(artifact.getId(), Collections.<String, Object>emptyMap());
        }
        String id = UUID.randomUUID().toString();
        Artifact artifact = Artifact.create(id, userId, runId, type, sceneCode,
                null, title, json, now);
        artifactRepository.save(artifact);
        return new PersistedGenerationArtifact(id, Collections.<String, Object>emptyMap());
    }

    static ArtifactType resolveType(String persistAs) {
        if (!StringUtils.hasText(persistAs) || SkillRunProfile.PERSIST_NONE.equals(persistAs.trim())) {
            return ArtifactType.fromCode(ArtifactType.CODE_CHAT);
        }
        return ArtifactType.fromCode(persistAs);
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
