package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.application.business.session.dto.SessionTurnDTO;
import com.xmut.ebus.application.business.session.support.SessionTurnAssembler;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.PiLogicalRunRef;
import com.xmut.ebus.domain.business.agent.model.PiMessage;
import com.xmut.ebus.domain.business.agent.model.PiSession;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SessionQuery：本人近 60 天会话列表与按逻辑 runId 聚合的回合回放。
 */
@Service
@RequiredArgsConstructor
public class SessionQueryService {

    public static final int SESSION_WINDOW_DAYS = 60;
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    /** 对外按「回合」分页 */
    public static final int TURN_PAGE_DEFAULT = 20;
    public static final int TURN_PAGE_MAX = 50;
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
        List<PiSession> rows = piSessionQueryRepository.selectByUserSince(uid, since, sceneCode, capped);
        List<SessionSummaryDTO> out = new ArrayList<SessionSummaryDTO>();
        for (PiSession row : rows) {
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

    /**
     * 按逻辑 runId 两段查询：① 分页 run ② 拉消息，再 Assembler 聚类（页内 tipSeq 升序）。
     */
    @Transactional(readOnly = true)
    public Page<SessionTurnDTO> getMessageList(String userId, String sessionId, String nextToken, Integer limit) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String sid = StringUtils.requireHasText(sessionId, "sessionId required");

        PiSession row = piSessionQueryRepository.findBySessionId(sid)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
        if (!uid.equals(row.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
        }

        int turnLimit = clampTurnPage(limit);
        Page<PiLogicalRunRef> runPage = piSessionQueryRepository.getLogicalRunIds(sid, nextToken, turnLimit);
        if (runPage == null || runPage.getItems() == null || runPage.getItems().isEmpty()) {
            return Page.empty();
        }
        List<PiLogicalRunRef> newestFirst = runPage.getItems();
        List<String> runIds = new ArrayList<String>(newestFirst.size());
        for (PiLogicalRunRef ref : newestFirst) {
            runIds.add(ref.getLogicalRunId());
        }
        List<PiMessage> messages = keepReplayMessages(
                piSessionQueryRepository.getMessagesByLogicalRunIds(sid, runIds));
        List<SessionTurnDTO> assembled = SessionTurnAssembler.assemble(messages);
        List<SessionTurnDTO> ascending = orderTurnsByRunTipOrder(assembled, newestFirst);
        return Page.of(ascending, runPage.getNextToken());
    }

    /** 按 ① 返回的 tipSeq 升序排放 turns（聊天新在后）。 */
    private static List<SessionTurnDTO> orderTurnsByRunTipOrder(
            List<SessionTurnDTO> assembled, List<PiLogicalRunRef> newestFirst) {
        Map<String, SessionTurnDTO> byRun = new HashMap<String, SessionTurnDTO>();
        for (SessionTurnDTO turn : assembled) {
            if (turn != null && StringUtils.hasText(turn.getRunId())) {
                byRun.put(turn.getRunId(), turn);
            }
        }
        List<SessionTurnDTO> out = new ArrayList<SessionTurnDTO>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            SessionTurnDTO turn = byRun.get(newestFirst.get(i).getLogicalRunId());
            if (turn != null) {
                out.add(turn);
            }
        }
        return out;
    }

    private static List<PiMessage> keepReplayMessages(List<PiMessage> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<PiMessage> kept = new ArrayList<PiMessage>();
        for (PiMessage message : items) {
            if (keepReplay(message)) {
                kept.add(message);
            }
        }
        return kept;
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

    static int clampTurnPage(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return TURN_PAGE_DEFAULT;
        }
        return Math.min(limit.intValue(), TURN_PAGE_MAX);
    }

    static boolean keepReplay(Message message) {
        if (message == null) {
            return false;
        }
        return keepReplay(message.getRole(), message.getContent());
    }

    static boolean keepReplay(String role, String content) {
        return keepReplay(role, content, false);
    }

    static boolean keepReplay(PiMessage message) {
        if (message == null) {
            return false;
        }
        boolean hasToolMeta = StringUtils.hasText(message.getToolCallId())
                || (message.getToolCalls() != null && !message.getToolCalls().isEmpty());
        return keepReplay(message.getRole(), message.getContent(), hasToolMeta);
    }

    static boolean keepReplay(String role, String content, boolean hasToolMeta) {
        if (role == null) {
            return false;
        }
        if ("tool".equalsIgnoreCase(role)) {
            return true;
        }
        if ("user".equalsIgnoreCase(role)) {
            return StringUtils.hasText(content);
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return StringUtils.hasText(content) || hasToolMeta;
        }
        return false;
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
