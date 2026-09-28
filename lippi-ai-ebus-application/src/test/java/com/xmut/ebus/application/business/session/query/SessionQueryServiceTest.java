package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private SessionQueryService service;

    @BeforeEach
    void setUp() {
        piSessionQueryRepository = mock(PiSessionQueryRepository.class);
        sessionStore = mock(SessionStore.class);
        service = new SessionQueryService(
                piSessionQueryRepository,
                sessionStore,
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
    void listMessagesSkipsSystemAndEmpty() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
        when(sessionStore.load(SESSION)).thenReturn(Arrays.asList(
                Message.system("hidden"),
                Message.user("   "),
                Message.user("你好"),
                Message.assistant("这是回复", null),
                Message.builder().role("tool").content("tool-output").build(),
                Message.assistant("", null)));

        List<SessionMessageDTO> messages = service.listMessages(USER, SESSION);

        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("你好", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("这是回复", messages.get(1).getContent());
    }

    @Test
    void listMessagesForbiddenForOtherUser() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, OTHER, "ecommerce", NOW)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listMessages(USER, SESSION));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals(SessionQueryService.MSG_UNAVAILABLE, ex.getMessage());
        verify(sessionStore, never()).load(any());
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
    void listMessagesForbiddenWhenUserIdNullOnRow() {
        when(piSessionQueryRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(meta(SESSION, null, "ecommerce", NOW)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listMessages(USER, SESSION));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(sessionStore, never()).load(any());
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
