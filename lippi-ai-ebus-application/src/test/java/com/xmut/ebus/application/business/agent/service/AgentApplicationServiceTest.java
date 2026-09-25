package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T05:00:00Z");
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String HOLD_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private CreditApplicationService creditApplicationService;
    @Mock
    private GenerationRunRepository generationRunRepository;
    @Mock
    private AgentSession agentSession;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AgentApplicationService(
                creditApplicationService,
                generationRunRepository,
                agentSession,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void prepareEmptyRunReservesAndPersistsRun() {
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        EmptyRunContext ctx = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .build());

        assertEquals(HOLD_ID, ctx.getHoldId());
        assertEquals(USER_ID, ctx.getUserId());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).save(captor.capture());
        GenerationRun saved = captor.getValue();
        assertEquals(HOLD_ID, saved.getHoldId());
        assertEquals(GenerationRunStatus.RUNNING, saved.getStatus());
        assertEquals(null, saved.getArtifactRef());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareEmptyRunStoresFixedSessionIdAndNewHoldEachTime() {
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn("hold-a", "hold-b");
        String sessionId = "fixed-session-id";

        EmptyRunContext first = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sessionId(sessionId)
                .build());
        EmptyRunContext second = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sessionId(sessionId)
                .build());

        assertEquals(sessionId, first.getSessionId());
        assertEquals(sessionId, second.getSessionId());
        assertEquals("hold-a", first.getHoldId());
        assertEquals("hold-b", second.getHoldId());
        assertNotEquals(first.getHoldId(), second.getHoldId());
        assertNotEquals(first.getRunId(), second.getRunId());

        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        for (GenerationRun saved : captor.getAllValues()) {
            assertEquals(sessionId, saved.getSessionId());
        }
    }

    @Test
    void prepareEmptyRunDoesNotCreateRunWhenInsufficient() {
        when(creditApplicationService.reserveOne(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder().userId(USER_ID).build()));
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunEmitsStartedDeltaFailedAndReleasesWithoutSettle() {
        EmptyRunContext ctx = new EmptyRunContext("run-1", USER_ID, HOLD_ID, "session-1");
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            Consumer<PiEvent> handler = subscriber.get();
            handler.accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, "hi"));
            return TurnResult.ok("run-1", "session-1", "hi",
                    Collections.<com.xmut.lims.pi.ai.message.Message>emptyList());
        });
        when(generationRunRepository.findById("run-1")).thenReturn(Optional.of(
                GenerationRun.start("run-1", USER_ID, HOLD_ID, "session-1", NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        Ad4SseEvent started = events.get(0);
        assertEquals(Ad4EventName.run_started, started.getName());
        assertEquals("run-1", started.getData().get("runId"));
        assertEquals(HOLD_ID, started.getData().get("holdId"));
        assertEquals("session-1", started.getData().get("sessionId"));

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        assertEquals(AgentApplicationService.EMPTY_RUN_FAIL_REASON, failed.getData().get("reason"));

        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunSynthesizesMessageDeltaWhenNoPiUpdate() {
        EmptyRunContext ctx = new EmptyRunContext("run-3", USER_ID, HOLD_ID, "session-3");
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-3", "session-3", "stub-final",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-3")).thenReturn(Optional.of(
                GenerationRun.start("run-3", USER_ID, HOLD_ID, "session-3", NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        Ad4SseEvent delta = null;
        for (Ad4SseEvent event : events) {
            if (event.getName() == Ad4EventName.message_delta) {
                delta = event;
                break;
            }
        }
        assertTrue(delta != null);
        assertEquals("stub-final", delta.getData().get("text"));
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        assertEquals(AgentApplicationService.EMPTY_RUN_FAIL_REASON, failed.getData().get("reason"));
    }

    @Test
    void streamEmptyRunReleasesOnAgentFailureAndStillDoesNotSettle() {
        EmptyRunContext ctx = new EmptyRunContext("run-2", USER_ID, HOLD_ID, "session-2");
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenThrow(new RuntimeException("agent boom"));
        when(generationRunRepository.findById("run-2")).thenReturn(Optional.of(
                GenerationRun.start("run-2", USER_ID, HOLD_ID, "session-2", NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        assertEquals(Ad4EventName.run_started, events.get(0).getName());
        assertEquals(Ad4EventName.run_failed, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(eq(USER_ID), eq(HOLD_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunReportsReleaseFailureReasonAndDoesNotClaimReleased() {
        EmptyRunContext ctx = new EmptyRunContext("run-4", USER_ID, HOLD_ID, "session-4");
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-4", "session-4", "ok",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-4")).thenReturn(Optional.of(
                GenerationRun.start("run-4", USER_ID, HOLD_ID, "session-4", NOW)));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        assertEquals(AgentApplicationService.RELEASE_FAILED_REASON, failed.getData().get("reason"));
        assertFalse(String.valueOf(failed.getData().get("reason")).contains("预占已释放"));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunAbortsOnSinkFailureThenReleasesAndEmitsRunFailed() {
        EmptyRunContext ctx = new EmptyRunContext("run-5", USER_ID, HOLD_ID, "session-5");
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            subscriber.get().accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, "chunk"));
            return TurnResult.ok("run-5", "session-5", "ok",
                    Collections.<com.xmut.lims.pi.ai.message.Message>emptyList());
        });
        when(generationRunRepository.findById("run-5")).thenReturn(Optional.of(
                GenerationRun.start("run-5", USER_ID, HOLD_ID, "session-5", NOW)));

        AtomicInteger accepts = new AtomicInteger();
        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, event -> {
            int n = accepts.incrementAndGet();
            if (n == 2) {
                throw new IllegalStateException("sse broken");
            }
            events.add(event);
        });

        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(AgentApplicationService.SSE_SEND_FAILED_RELEASED, failed.getData().get("reason"));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }
}
