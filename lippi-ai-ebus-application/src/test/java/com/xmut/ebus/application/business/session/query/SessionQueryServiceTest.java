package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
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

        List<SessionSummaryDTO> list = service.list(USER, null, null);

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
    void getMessageListReturnsPageWithNextToken() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        String dump = "```json\n{\"view\":{\"version\":1,\"blocks\":[]}}\n```";
        Instant t1 = NOW.minus(3, ChronoUnit.MINUTES);
        Instant t2 = NOW.minus(2, ChronoUnit.MINUTES);
        Instant t3 = NOW.minus(1, ChronoUnit.MINUTES);
        when(piSessionQueryRepository.getMessageList(eq(SESSION), isNull(), eq(100)))
                .thenReturn(Page.of(Arrays.asList(
                        new PiMessageDTO("user", "你好", t1, 3L),
                        new PiMessageDTO("assistant", "这是回复", t2, 4L),
                        new PiMessageDTO("assistant", dump, t3, 6L)), "3"));

        Page<SessionMessageDTO> page = service.getMessageList(USER, SESSION, null, null);

        assertEquals("3", page.getNextToken());
        assertEquals(3, page.getItems().size());
        assertEquals("user", page.getItems().get(0).getRole());
        assertEquals("你好", page.getItems().get(0).getContent());
        assertEquals(t1, page.getItems().get(0).getCreatedAt());
        assertEquals(Long.valueOf(3L), page.getItems().get(0).getSeq());
        assertEquals("assistant", page.getItems().get(1).getRole());
        assertEquals(Long.valueOf(4L), page.getItems().get(1).getSeq());
        assertEquals(dump, page.getItems().get(2).getContent());
        assertEquals(Long.valueOf(6L), page.getItems().get(2).getSeq());
        verify(sessionStore, never()).load(any());
    }

    @Test
    void getMessageListPassesNextTokenAndClampsLimit() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(piSessionQueryRepository.getMessageList(eq(SESSION), eq("10"), eq(100)))
                .thenReturn(Page.<PiMessageDTO>empty());

        Page<SessionMessageDTO> page = service.getMessageList(USER, SESSION, "10", 500);

        assertNull(page.getNextToken());
        assertTrue(page.getItems().isEmpty());
        verify(piSessionQueryRepository).getMessageList(SESSION, "10", 100);
    }

    @Test
    void getMessageListForbiddenForOtherUser() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, OTHER, "ecommerce", NOW)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getMessageList(USER, SESSION, null, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals(SessionQueryService.MSG_UNAVAILABLE, ex.getMessage());
        verify(piSessionQueryRepository, never()).getMessageList(anyString(), any(), anyInt());
    }

    @Test
    void listTruncatesTitleToFortyCharsAndDefaultsWhenNoUserText() {
        String longText = repeat('啊', 45);
        when(piSessionQueryRepository.selectByUserSince(eq(USER), any(Instant.class), eq("ecommerce"), eq(50)))
                .thenReturn(Collections.singletonList(meta(SESSION, USER, "ecommerce", NOW)));
        when(sessionStore.load(SESSION)).thenReturn(Collections.singletonList(Message.user(longText)));

        List<SessionSummaryDTO> titled = service.list(USER, "ecommerce", null);
        assertEquals(40, titled.get(0).getTitle().length());
        assertEquals(longText.substring(0, 40), titled.get(0).getTitle());

        when(sessionStore.load(SESSION)).thenReturn(Collections.singletonList(Message.assistant("仅助手", null)));
        List<SessionSummaryDTO> fallback = service.list(USER, "ecommerce", null);
        assertEquals(SessionQueryService.DEFAULT_TITLE, fallback.get(0).getTitle());
    }

    @Test
    void listClampsLimitToMaxOneHundred() {
        when(piSessionQueryRepository.selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(100)))
                .thenReturn(Collections.emptyList());
        assertTrue(service.list(USER, null, 500).isEmpty());
        verify(piSessionQueryRepository).selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(100));
    }

    @Test
    void getMessageListForbiddenWhenUserIdNullOnRow() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, null, "ecommerce", NOW)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getMessageList(USER, SESSION, null, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(piSessionQueryRepository, never()).getMessageList(anyString(), any(), anyInt());
    }

    @Test
    void latestArtifactReturnsHistoryDetailForLatestUsableRef() {
        HistoryArtifactDetailDTO expected = new HistoryArtifactDetailDTO(
                "sku-9", "sku", "ecommerce", "Listing", NOW, Collections.emptyMap(), SESSION);
        Instant since = NOW.minus(60, ChronoUnit.DAYS);
        when(generationRunRepository.findLatestSettledArtifactRefBySession(USER, SESSION, since))
                .thenReturn(Optional.of("sku-9"));
        when(historyQueryService.findById(USER, "sku-9")).thenReturn(expected);

        Optional<HistoryArtifactDetailDTO> found = service.latestArtifact(USER, SESSION);

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

        Optional<HistoryArtifactDetailDTO> found = service.latestArtifact(USER, SESSION);

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

        Optional<HistoryArtifactDetailDTO> found = service.latestArtifact(USER, SESSION);

        assertFalse(found.isPresent());
    }

    private static PiSessionMeta meta(String sessionId, String userId, String sceneCode, Instant updatedAt) {
        return new PiSessionMeta(sessionId, userId, sceneCode, null, updatedAt);
    }

    private static String repeat(char ch, int n) {
        char[] buf = new char[n];
        Arrays.fill(buf, ch);
        return new String(buf);
    }
}
