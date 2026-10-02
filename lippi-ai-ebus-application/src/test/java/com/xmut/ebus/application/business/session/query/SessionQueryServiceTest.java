package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.application.business.session.dto.SessionTurnDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.domain.business.agent.model.PiLogicalRunRef;
import com.xmut.ebus.domain.business.agent.model.PiMessage;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
import com.xmut.ebus.domain.business.agent.model.PiToolCallRef;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.agent.repository.PiSessionQueryRepository;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final String USER = "user-1";
    private static final String OTHER = "user-2";
    private static final String SESSION = "sess-1";

    private PiSessionQueryRepository piSessionQueryRepository;
    private SessionStore sessionStore;
    private GenerationRunRepository generationRunRepository;
    private HistoryQueryService historyQueryService;
    private SessionQueryService service;

    @BeforeEach
    void setUp() {
        piSessionQueryRepository = mock(PiSessionQueryRepository.class);
        sessionStore = mock(SessionStore.class);
        generationRunRepository = mock(GenerationRunRepository.class);
        historyQueryService = mock(HistoryQueryService.class);
        service = new SessionQueryService(
                piSessionQueryRepository,
                sessionStore,
                generationRunRepository,
                historyQueryService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listOnlyReturnsCurrentUsersSessions() {
        PiSessionMeta own = meta(SESSION, USER, "ecommerce", NOW.minus(1, ChronoUnit.HOURS));
        PiSessionMeta leaked = meta("sess-other", OTHER, "ecommerce", NOW.minus(2, ChronoUnit.HOURS));
        when(piSessionQueryRepository.selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(50)))
                .thenReturn(Arrays.asList(own, leaked));
        when(sessionStore.load(SESSION)).thenReturn(Collections.singletonList(Message.user("找水杯")));
        when(sessionStore.load("sess-other")).thenReturn(Collections.singletonList(Message.user("不该出现")));

        List<SessionSummaryDTO> list = service.list(SessionListQuery.builder()
                .userId(USER).sceneCode(null).limit(null).build());

        assertEquals(1, list.size());
        assertEquals(SESSION, list.get(0).getSessionId());
        assertEquals("找水杯", list.get(0).getTitle());
        assertEquals("ecommerce", list.get(0).getSceneCode());

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(piSessionQueryRepository).selectByUserSince(
                eq(USER), sinceCaptor.capture(), isNull(), eq(50));
        assertEquals(NOW.minus(60, ChronoUnit.DAYS), sinceCaptor.getValue());
        verify(sessionStore, never()).load("sess-other");
    }

    @Test
    void pageTurnsReturnsTurnPageWithNextToken() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), isNull(), eq(20)))
                .thenReturn(Page.of(Collections.singletonList(new PiLogicalRunRef("run-a", 6L)), "6"));
        Instant t1 = NOW.minus(3, ChronoUnit.MINUTES);
        String dump = "```json\n{\"view\":{\"version\":1,\"blocks\":[]}}\n```";
        when(piSessionQueryRepository.getMessagesByLogicalRunIds(eq(SESSION), eq(Collections.singletonList("run-a"))))
                .thenReturn(Arrays.asList(
                        new PiMessage("user", "你好", t1, 3L, null, Collections.<PiToolCallRef>emptyList(), "run-a"),
                        new PiMessage("assistant", dump, t1, 6L, null, Collections.<PiToolCallRef>emptyList(), "run-a")));

        Page<SessionTurnDTO> page = service.pageTurns(SessionTurnPageQuery.builder()
                .userId(USER).sessionId(SESSION).nextToken(null).limit(null).build());

        assertEquals("6", page.getNextToken());
        assertEquals(1, page.getItems().size());
        assertEquals("run-a", page.getItems().get(0).getRunId());
        assertEquals("你好", page.getItems().get(0).getUserPrompt());
    }

    @Test
    void pageTurnsClustersListingSuspendResumeIntoOneTurn() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), isNull(), eq(20)))
                .thenReturn(Page.of(Collections.singletonList(new PiLogicalRunRef("list-9", 4L)), null));
        Instant t = NOW.minus(1, ChronoUnit.MINUTES);
        when(piSessionQueryRepository.getMessagesByLogicalRunIds(eq(SESSION), eq(Collections.singletonList("list-9"))))
                .thenReturn(Arrays.asList(
                        msg("user", "请生成上架素材", 1L, t, "list-9:suspend", null, null),
                        msg("assistant", "{\"output\":\"plan/final.json\"}", 2L, t, "list-9:suspend", null, null),
                        msg("user", "{\"optionId\":\"confirm_execute\"}", 3L, t, "list-9:resume", null, null),
                        msg("assistant", "{\"output\":\"exec/final.json\"}", 4L, t, "list-9:resume", null, null)));

        Page<SessionTurnDTO> page = service.pageTurns(SessionTurnPageQuery.builder()
                .userId(USER).sessionId(SESSION).nextToken(null).limit(null).build());

        assertNull(page.getNextToken());
        assertEquals(1, page.getItems().size());
        assertEquals("list-9", page.getItems().get(0).getRunId());
        assertEquals(4, page.getItems().get(0).getMessages().size());
    }

    @Test
    void pageTurnsPassesNextTokenAndClampsLimit() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), eq("10"), eq(50)))
                .thenReturn(Page.<PiLogicalRunRef>empty());

        Page<SessionTurnDTO> page = service.pageTurns(SessionTurnPageQuery.builder()
                .userId(USER).sessionId(SESSION).nextToken("10").limit(500).build());

        assertTrue(page.getItems().isEmpty());
        verify(piSessionQueryRepository).getLogicalRunIds(SESSION, "10", 50);
        verify(piSessionQueryRepository, never()).getMessagesByLogicalRunIds(anyString(), anyList());
    }

    @Test
    void pageTurnsOrdersTurnsByTipSeqAscending() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), isNull(), eq(20)))
                .thenReturn(Page.of(Arrays.asList(
                        new PiLogicalRunRef("run-new", 80L),
                        new PiLogicalRunRef("run-old", 40L)), "40"));
        Instant tOld = NOW.minus(10, ChronoUnit.MINUTES);
        Instant tNew = NOW.minus(2, ChronoUnit.MINUTES);
        when(piSessionQueryRepository.getMessagesByLogicalRunIds(
                eq(SESSION), eq(Arrays.asList("run-new", "run-old"))))
                .thenReturn(Arrays.asList(
                        msg("user", "先问", 39L, tOld, "run-old", null, null),
                        msg("assistant", "先答", 40L, tOld, "run-old", null, null),
                        msg("user", "后问", 79L, tNew, "run-new", null, null),
                        msg("assistant", "后答", 80L, tNew, "run-new", null, null)));

        Page<SessionTurnDTO> page = service.pageTurns(SessionTurnPageQuery.builder()
                .userId(USER).sessionId(SESSION).nextToken(null).limit(null).build());

        assertEquals("40", page.getNextToken());
        assertEquals(2, page.getItems().size());
        assertEquals("run-old", page.getItems().get(0).getRunId());
        assertEquals("先问", page.getItems().get(0).getUserPrompt());
        assertEquals("run-new", page.getItems().get(1).getRunId());
        assertEquals("后问", page.getItems().get(1).getUserPrompt());
    }

    @Test
    void pageTurnsForbiddenForOtherUser() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, OTHER, "ecommerce", NOW)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.pageTurns(SessionTurnPageQuery.builder()
                        .userId(USER).sessionId(SESSION).nextToken(null).limit(null).build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals(SessionQueryService.MSG_UNAVAILABLE, ex.getMessage());
        verify(piSessionQueryRepository, never()).getLogicalRunIds(anyString(), any(), anyInt());
    }

    @Test
    void listTruncatesTitleToFortyCharsAndDefaultsWhenNoUserText() {
        String longText = repeat('啊', 45);
        when(piSessionQueryRepository.selectByUserSince(eq(USER), any(Instant.class), eq("ecommerce"), eq(50)))
                .thenReturn(Collections.singletonList(meta(SESSION, USER, "ecommerce", NOW)));
        when(sessionStore.load(SESSION)).thenReturn(Collections.singletonList(Message.user(longText)));

        List<SessionSummaryDTO> titled = service.list(SessionListQuery.builder()
                .userId(USER).sceneCode("ecommerce").limit(null).build());
        assertEquals(40, titled.get(0).getTitle().length());
        assertEquals(longText.substring(0, 40), titled.get(0).getTitle());

        when(sessionStore.load(SESSION)).thenReturn(Collections.singletonList(Message.assistant("仅助手", null)));
        List<SessionSummaryDTO> fallback = service.list(SessionListQuery.builder()
                .userId(USER).sceneCode("ecommerce").limit(null).build());
        assertEquals(SessionQueryService.DEFAULT_TITLE, fallback.get(0).getTitle());
    }

    @Test
    void listClampsLimitToMaxOneHundred() {
        when(piSessionQueryRepository.selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(100)))
                .thenReturn(Collections.emptyList());
        assertTrue(service.list(SessionListQuery.builder().userId(USER).limit(500).build()).isEmpty());
        verify(piSessionQueryRepository).selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(100));
    }

    @Test
    void pageTurnsForbiddenWhenUserIdNullOnRow() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, null, "ecommerce", NOW)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.pageTurns(SessionTurnPageQuery.builder()
                        .userId(USER).sessionId(SESSION).nextToken(null).limit(null).build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(piSessionQueryRepository, never()).getLogicalRunIds(anyString(), any(), anyInt());
    }

    @Test
    void latestArtifactFetchesSessionWithOnlyXhsArtifact() {
        HistoryArtifactDetailDTO expected = new HistoryArtifactDetailDTO(
                "xhs-break-1", "xhs_break", "xiaohongshu", "爆文拆解", NOW,
                Collections.emptyMap(), SESSION);
        Instant since = NOW.minus(60, ChronoUnit.DAYS);
        when(generationRunRepository.findLatestSettledArtifactRefBySession(USER, SESSION, since))
                .thenReturn(Optional.of("xhs-break-1"));
        when(historyQueryService.findById(USER, "xhs-break-1")).thenReturn(expected);

        Optional<HistoryArtifactDetailDTO> found = service.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(USER).sessionId(SESSION).build());

        assertTrue(found.isPresent());
        assertEquals("xhs_break", found.get().getArtifactType());
        assertEquals("xhs-break-1", found.get().getId());
        verify(historyQueryService).findById(USER, "xhs-break-1");
    }

    @Test
    void latestArtifactReturnsHistoryDetailForLatestUsableRef() {
        HistoryArtifactDetailDTO expected = new HistoryArtifactDetailDTO(
                "sku-9", "sku", "ecommerce", "Listing", NOW, Collections.emptyMap(), SESSION);
        Instant since = NOW.minus(60, ChronoUnit.DAYS);
        when(generationRunRepository.findLatestSettledArtifactRefBySession(USER, SESSION, since))
                .thenReturn(Optional.of("sku-9"));
        when(historyQueryService.findById(USER, "sku-9")).thenReturn(expected);

        Optional<HistoryArtifactDetailDTO> found = service.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(USER).sessionId(SESSION).build());

        assertTrue(found.isPresent());
        assertEquals("sku-9", found.get().getId());
        assertEquals(SESSION, found.get().getSessionId());
        verify(generationRunRepository).findLatestSettledArtifactRefBySession(USER, SESSION, since);
        verify(historyQueryService).findById(USER, "sku-9");
    }

    @Test
    void latestArtifactEmptyWhenNoUsableRun() {
        Instant since = NOW.minus(60, ChronoUnit.DAYS);
        when(generationRunRepository.findLatestSettledArtifactRefBySession(USER, SESSION, since))
                .thenReturn(Optional.empty());

        Optional<HistoryArtifactDetailDTO> found = service.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(USER).sessionId(SESSION).build());

        assertFalse(found.isPresent());
        verify(historyQueryService, never()).findById(any(), any());
    }

    @Test
    void latestArtifactEmptyWhenHistoryFindByIdForbidden() {
        Instant since = NOW.minus(60, ChronoUnit.DAYS);
        when(generationRunRepository.findLatestSettledArtifactRefBySession(USER, SESSION, since))
                .thenReturn(Optional.of("sku-old"));
        when(historyQueryService.findById(USER, "sku-old"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, HistoryQueryService.MSG_UNAVAILABLE));

        Optional<HistoryArtifactDetailDTO> found = service.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(USER).sessionId(SESSION).build());

        assertFalse(found.isPresent());
    }

    private static PiSessionMeta meta(String sessionId, String userId, String sceneCode, Instant updatedAt) {
        return new PiSessionMeta(sessionId, userId, sceneCode, null, updatedAt);
    }

    private static PiMessage msg(
            String role,
            String content,
            long seq,
            Instant at,
            String runId,
            String toolCallId,
            List<PiToolCallRef> toolCalls) {
        return new PiMessage(
                role,
                content,
                at,
                seq,
                toolCallId,
                toolCalls == null ? Collections.<PiToolCallRef>emptyList() : toolCalls,
                runId);
    }

    private static String repeat(char ch, int n) {
        char[] buf = new char[n];
        Arrays.fill(buf, ch);
        return new String(buf);
    }
}
