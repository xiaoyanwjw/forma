package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.agent.repository.PiSessionQueryRepository;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.session.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SessionQuery：本人近 60 天会话列表与 R1 消息（显式 userId ACL）。
 */
@Service
@RequiredArgsConstructor
public class SessionQueryService {

    public static final int SESSION_WINDOW_DAYS = 60;
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    public static final String DEFAULT_TITLE = "电商会话";
    public static final String MSG_UNAVAILABLE = "会话不存在或无权查看";

    private static final int TITLE_MAX_CHARS = 40;

    private final PiSessionQueryRepository piSessionQueryRepository;
    private final SessionStore sessionStore;
    private final GenerationRunRepository generationRunRepository;
    private final HistoryQueryService historyQueryService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SessionSummaryDTO> list(String userId, String sceneCodeOrNull, Integer limit) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sceneCode = StringUtils.hasText(sceneCodeOrNull) ? sceneCodeOrNull.trim() : null;
        int capped = clampLimit(limit);
        Instant since = Instant.now(clock).minus(SESSION_WINDOW_DAYS, ChronoUnit.DAYS);
        List<PiSessionMeta> rows = piSessionQueryRepository.selectByUserSince(uid, since, sceneCode, capped);
        List<SessionSummaryDTO> out = new ArrayList<SessionSummaryDTO>();
        for (PiSessionMeta row : rows) {
            if (row == null || !uid.equals(row.getUserId()) || !StringUtils.hasText(row.getSessionId())) {
                continue;
            }
            List<Message> messages = sessionStore.load(row.getSessionId());
            out.add(new SessionSummaryDTO(
                    row.getSessionId(),
                    titleFrom(messages),
                    row.getSceneCode(),
                    row.getUpdatedAt()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<SessionMessageDTO> listMessages(String userId, String sessionId) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sid = StringUtils.requireHasText(sessionId, "sessionId required");
        PiSessionMeta row = piSessionQueryRepository.findBySessionId(sid)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
        if (!uid.equals(row.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }
        List<Message> loaded = sessionStore.load(sid);
        List<SessionMessageDTO> out = new ArrayList<SessionMessageDTO>();
        if (loaded == null) {
            return out;
        }
        for (Message message : loaded) {
            if (!keepReplay(message)) {
                continue;
            }
            String role = message.getRole().toLowerCase(Locale.ROOT);
            out.add(new SessionMessageDTO(role, message.getContent(), null));
        }
        return out;
    }

    /**
     * 会话侧栏最近可用成果：本人该 session 上最新 picklist/sku artifact_ref，详情走 HistoryQuery（含 resign / 60 天窗）。
     */
    @Transactional(readOnly = true)
    public Optional<HistoryArtifactDetailDTO> latestArtifact(String userId, String sessionId) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sid = StringUtils.requireHasText(sessionId, "sessionId required");
        Optional<String> artifactId = generationRunRepository.findLatestSettledArtifactRefBySession(uid, sid);
        if (!artifactId.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(historyQueryService.findById(uid, artifactId.get()));
    }

    static int clampLimit(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit.intValue(), MAX_LIMIT);
    }

    static boolean keepReplay(Message message) {
        if (message == null || !StringUtils.hasText(message.getContent())) {
            return false;
        }
        String role = message.getRole();
        return role != null
                && ("user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role));
    }

    static String titleFrom(List<Message> messages) {
        if (messages == null) {
            return DEFAULT_TITLE;
        }
        for (Message message : messages) {
            if (message == null || !"user".equalsIgnoreCase(message.getRole())
                    || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String content = message.getContent().trim();
            if (content.length() <= TITLE_MAX_CHARS) {
                return content;
            }
            return content.substring(0, TITLE_MAX_CHARS);
        }
        return DEFAULT_TITLE;
    }
}
