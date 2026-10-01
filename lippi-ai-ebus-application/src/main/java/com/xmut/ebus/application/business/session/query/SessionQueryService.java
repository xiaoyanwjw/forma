package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
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
    /** 消息回放默认 / 最大页大小 */
    public static final int MESSAGE_PAGE_DEFAULT = 100;
    public static final int MESSAGE_PAGE_MAX = 100;
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
    public Page<SessionMessageDTO> getMessageList(String userId, String sessionId, String nextToken, Integer limit) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sid = StringUtils.requireHasText(sessionId, "sessionId required");

        PiSessionMeta row = piSessionQueryRepository.findBySessionId(sid)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
        if (!uid.equals(row.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }
        
        int pageSize = clampMessagePage(limit);
        Page<PiMessageDTO> page = piSessionQueryRepository.getMessageList(sid, nextToken, pageSize);
        List<SessionMessageDTO> out = new ArrayList<SessionMessageDTO>();
        if (page != null) {
            for (PiMessageDTO message : page.getItems()) {
                if (!keepReplay(message)) {
                    continue;
                }
                String role = message.getRole().toLowerCase(Locale.ROOT);
                out.add(new SessionMessageDTO(
                        role,
                        message.getContent(),
                        message.getCreatedAt(),
                        message.getSeq()));
            }
        }
        String token = page == null ? null : page.getNextToken();
        return Page.of(out, token);
    }

    /**
     * 会话侧栏最近可用成果：本人该 session 上最新 history 类型 artifact_ref，详情走 HistoryQuery（含 resign / 60 天窗）。
     * {@code artifactType} 可选：{@code picklist} / {@code sku} / {@code xhs_topiclist} / {@code xhs_note} / {@code xhs_break}；
     * 空则上述类型里取最新一条。
     */
    @Transactional(readOnly = true)
    public Optional<HistoryArtifactDetailDTO> latestArtifact(String userId, String sessionId) {
        return latestArtifact(userId, sessionId, null);
    }

    @Transactional(readOnly = true)
    public Optional<HistoryArtifactDetailDTO> latestArtifact(String userId, String sessionId, String artifactType) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sid = StringUtils.requireHasText(sessionId, "sessionId required");
        Instant since = Instant.now(clock).minus(HistoryQueryService.HISTORY_WINDOW_DAYS, ChronoUnit.DAYS);
        Optional<String> artifactId;
        if (StringUtils.hasText(artifactType)) {
            artifactId = generationRunRepository.findLatestSettledArtifactRefBySession(
                    uid, sid, since, artifactType.trim());
        } else {
            artifactId = generationRunRepository.findLatestSettledArtifactRefBySession(uid, sid, since);
        }
        if (!artifactId.isPresent()) {
            return Optional.empty();
        }
        try {
            return Optional.of(historyQueryService.findById(uid, artifactId.get()));
        } catch (BusinessException ex) {
            return Optional.empty();
        }
    }

    static int clampLimit(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit.intValue(), MAX_LIMIT);
    }

    static int clampMessagePage(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return MESSAGE_PAGE_DEFAULT;
        }
        return Math.min(limit.intValue(), MESSAGE_PAGE_MAX);
    }

    static boolean keepReplay(Message message) {
        if (message == null) {
            return false;
        }
        return keepReplay(message.getRole(), message.getContent());
    }

    static boolean keepReplay(PiMessageDTO message) {
        if (message == null) {
            return false;
        }
        return keepReplay(message.getRole(), message.getContent());
    }

    static boolean keepReplay(String role, String content) {
        if (!StringUtils.hasText(content) || role == null) {
            return false;
        }
        return "user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role);
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
