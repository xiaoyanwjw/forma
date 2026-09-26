package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.model.SessionSceneBinding;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.agent.repository.PiSessionSceneRepository;
import com.xmut.ebus.domain.business.scene.constant.SceneStatus;
import com.xmut.ebus.domain.business.scene.model.Scene;
import com.xmut.ebus.domain.business.scene.repository.SceneRepository;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.ToolLevel;
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
import java.util.Arrays;
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
    private static final String ECOM_SCENE_ID = "a1000001-0001-4000-8000-000000000001";
    private static final String ECOM_SCENE_CODE = "ecommerce";
    private static final String GRAY_SCENE_ID = "a1000001-0001-4000-8000-000000000002";
    private static final String GRAY_SCENE_CODE = "short_video";

    @Mock
    private CreditApplicationService creditApplicationService;
    @Mock
    private GenerationRunRepository generationRunRepository;
    @Mock
    private PiSessionSceneRepository piSessionSceneRepository;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private SceneCapabilityPackLoader sceneCapabilityPackLoader;
    @Mock
    private AgentSession agentSession;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AgentApplicationService(
                creditApplicationService,
                generationRunRepository,
                piSessionSceneRepository,
                sceneRepository,
                sceneCapabilityPackLoader,
                agentSession,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void prepareEmptyRunReservesAndPersistsRunWithSceneByCode() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        EmptyRunContext ctx = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        assertEquals(HOLD_ID, ctx.getHoldId());
        assertEquals(USER_ID, ctx.getUserId());
        assertEquals(ECOM_SCENE_CODE, ctx.getSceneCode());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).save(captor.capture());
        GenerationRun saved = captor.getValue();
        assertEquals(HOLD_ID, saved.getHoldId());
        assertEquals(GenerationRunStatus.RUNNING, saved.getStatus());
        assertEquals(null, saved.getArtifactRef());
        assertEquals(ECOM_SCENE_ID, saved.getSceneId());
        assertEquals(ECOM_SCENE_CODE, saved.getSceneCode());
        verify(piSessionSceneRepository).ensureBound(eq(ctx.getSessionId()), eq(ECOM_SCENE_ID), eq(ECOM_SCENE_CODE));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareEmptyRunSucceedsWithSceneIdOnly() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        EmptyRunContext ctx = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
                .build());

        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).save(captor.capture());
        assertEquals(ECOM_SCENE_ID, captor.getValue().getSceneId());
        assertEquals(ECOM_SCENE_CODE, captor.getValue().getSceneCode());
        verify(piSessionSceneRepository).ensureBound(eq(ctx.getSessionId()), eq(ECOM_SCENE_ID), eq(ECOM_SCENE_CODE));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareEmptyRunSucceedsWhenSceneIdAndCodeConsistent() {
        Scene ecommerce = ecommerceScene();
        when(sceneRepository.findByBizId(ECOM_SCENE_ID)).thenReturn(Optional.of(ecommerce));
        when(sceneRepository.findBySceneCode(ECOM_SCENE_CODE)).thenReturn(Optional.of(ecommerce));
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        verify(generationRunRepository).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareEmptyRunRejectsMissingSceneWithoutReserveOrSave() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder().userId(USER_ID).build()));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals(AgentApplicationService.MSG_SCENE_REQUIRED, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).ensureBound(anyString(), anyString(), anyString());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareEmptyRunRejectsUnknownSceneCode() {
        when(sceneRepository.findBySceneCode("unknown")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode("unknown")
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_FOUND, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareEmptyRunRejectsUnknownSceneId() {
        when(sceneRepository.findByBizId("missing-id")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sceneId("missing-id")
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_FOUND, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareEmptyRunRejectsConflictingSceneIdAndCode() {
        when(sceneRepository.findByBizId(ECOM_SCENE_ID)).thenReturn(Optional.of(ecommerceScene()));
        when(sceneRepository.findBySceneCode(GRAY_SCENE_CODE)).thenReturn(Optional.of(grayScene()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sceneId(ECOM_SCENE_ID)
                        .sceneCode(GRAY_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_MISMATCH, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareEmptyRunRejectsComingSoonScene() {
        when(sceneRepository.findBySceneCode(GRAY_SCENE_CODE)).thenReturn(Optional.of(grayScene()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode(GRAY_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_OPEN, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareEmptyRunRejectsSessionBoundToOtherScene() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("fixed-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session", GRAY_SCENE_ID, GRAY_SCENE_CODE)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sessionId("fixed-session")
                        .sceneCode(ECOM_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SESSION_SCENE_MISMATCH, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).ensureBound(anyString(), anyString(), anyString());
    }

    @Test
    void prepareEmptyRunWritesSceneWhenSessionHasNoneYet() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("legacy-session"))
                .thenReturn(Optional.of(new SessionSceneBinding("legacy-session", null, null)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sessionId("legacy-session")
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        verify(piSessionSceneRepository).ensureBound("legacy-session", ECOM_SCENE_ID, ECOM_SCENE_CODE);
    }

    @Test
    void prepareEmptyRunStoresFixedSessionIdAndNewHoldEachTime() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("fixed-session-id"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session-id", ECOM_SCENE_ID, ECOM_SCENE_CODE)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn("hold-a", "hold-b");
        String sessionId = "fixed-session-id";

        EmptyRunContext first = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sessionId(sessionId)
                .sceneCode(ECOM_SCENE_CODE)
                .build());
        EmptyRunContext second = service.prepareEmptyRun(StartEmptyRunCommand.builder()
                .userId(USER_ID)
                .sessionId(sessionId)
                .sceneCode(ECOM_SCENE_CODE)
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
            assertEquals(ECOM_SCENE_CODE, saved.getSceneCode());
        }
    }

    @Test
    void prepareEmptyRunDoesNotCreateRunWhenInsufficient() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareEmptyRun(StartEmptyRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode(ECOM_SCENE_CODE)
                        .build()));
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunEmitsStartedDeltaToRunFailedAndReleasesWithoutSettle() {
        EmptyRunContext ctx = emptyCtx("run-1", "session-1");
        stubEcommercePack();
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
                GenerationRun.start("run-1", USER_ID, HOLD_ID, "session-1",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

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

        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals(SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID, promptCaptor.getValue().getSkillId());

        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunSynthesizesMessageDeltaWhenNoPiUpdate() {
        EmptyRunContext ctx = emptyCtx("run-3", "session-3");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-3", "session-3", "stub-final",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-3")).thenReturn(Optional.of(
                GenerationRun.start("run-3", USER_ID, HOLD_ID, "session-3",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

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
        EmptyRunContext ctx = emptyCtx("run-2", "session-2");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenThrow(new RuntimeException("agent boom"));
        when(generationRunRepository.findById("run-2")).thenReturn(Optional.of(
                GenerationRun.start("run-2", USER_ID, HOLD_ID, "session-2",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        assertEquals(Ad4EventName.run_started, events.get(0).getName());
        assertEquals(Ad4EventName.run_failed, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(eq(USER_ID), eq(HOLD_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunReportsReleaseFailureReasonAndDoesNotClaimReleased() {
        EmptyRunContext ctx = emptyCtx("run-4", "session-4");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-4", "session-4", "ok",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-4")).thenReturn(Optional.of(
                GenerationRun.start("run-4", USER_ID, HOLD_ID, "session-4",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));
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
    void streamEmptyRunAbortsOnSinkFailureThenReleasesAndEmitsRunToRunFailed() {
        EmptyRunContext ctx = emptyCtx("run-5", "session-5");
        stubEcommercePack();
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
                GenerationRun.start("run-5", USER_ID, HOLD_ID, "session-5",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

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

    @Test
    void streamEmptyRunFailsHumanWithoutPromptWhenPackMissing() {
        EmptyRunContext ctx = emptyCtx("run-pack-miss", "session-pack-miss");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID,
                        SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE));
        when(generationRunRepository.findById("run-pack-miss")).thenReturn(Optional.of(
                GenerationRun.start("run-pack-miss", USER_ID, HOLD_ID, "session-pack-miss",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        assertEquals(Ad4EventName.run_started, events.get(0).getName());
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(agentSession, never()).subscribe(any());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunFailsHumanWithoutPromptWhenDefaultSkillMissing() {
        EmptyRunContext ctx = emptyCtx("run-default-miss", "session-default-miss");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(
                new SceneCapabilityPack(ECOM_SCENE_CODE, Collections.singletonList(
                        skill(SceneCapabilityPackLoader.SKILL_SKULIST,
                                "classpath:scenes/ecommerce/ecommerce.skulist.md"))));
        when(generationRunRepository.findById("run-default-miss")).thenReturn(Optional.of(
                GenerationRun.start("run-default-miss", USER_ID, HOLD_ID, "session-default-miss",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(agentSession, never()).subscribe(any());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunReportsReleaseFailureWhenPackMissing() {
        EmptyRunContext ctx = emptyCtx("run-pack-miss-release", "session-pack-miss-release");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID,
                        SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE));
        when(generationRunRepository.findById("run-pack-miss-release")).thenReturn(Optional.of(
                GenerationRun.start("run-pack-miss-release", USER_ID, HOLD_ID, "session-pack-miss-release",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(AgentApplicationService.RELEASE_FAILED_REASON, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    private EmptyRunContext emptyCtx(String runId, String sessionId) {
        return new EmptyRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE);
    }

    private void stubEcommercePack() {
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
    }

    private static SceneCapabilityPack ecommercePack() {
        return new SceneCapabilityPack(ECOM_SCENE_CODE, Arrays.asList(
                skill(SceneCapabilityPackLoader.SKILL_PICKLIST, "classpath:scenes/ecommerce/ecommerce.picklist.md"),
                skill(SceneCapabilityPackLoader.SKILL_SKULIST, "classpath:scenes/ecommerce/ecommerce.skulist.md")));
    }

    private static SkillManifest skill(String id, String promptRef) {
        return SkillManifest.builder()
                .id(id)
                .version("1.0.0")
                .displayName(id)
                .description(id)
                .promptRef(promptRef)
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build();
    }

    private void stubEcommerceByCode() {
        when(sceneRepository.findBySceneCode(ECOM_SCENE_CODE)).thenReturn(Optional.of(ecommerceScene()));
    }

    private void stubEcommerceById() {
        when(sceneRepository.findByBizId(ECOM_SCENE_ID)).thenReturn(Optional.of(ecommerceScene()));
    }

    private static Scene ecommerceScene() {
        Scene scene = new Scene();
        scene.setId(ECOM_SCENE_ID);
        scene.setSceneCode(ECOM_SCENE_CODE);
        scene.setDisplayName("电商开店");
        scene.setStatus(SceneStatus.AVAILABLE);
        scene.setSortOrder(1);
        return scene;
    }

    private static Scene grayScene() {
        Scene scene = new Scene();
        scene.setId(GRAY_SCENE_ID);
        scene.setSceneCode(GRAY_SCENE_CODE);
        scene.setDisplayName("短视频带货");
        scene.setStatus(SceneStatus.COMING_SOON);
        scene.setSortOrder(2);
        return scene;
    }
}
