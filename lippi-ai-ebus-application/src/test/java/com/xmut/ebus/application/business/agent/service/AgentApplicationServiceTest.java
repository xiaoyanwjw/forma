package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.command.StartPicklistRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.dto.PicklistRunContext;
import com.xmut.ebus.application.business.agent.support.SkillRunProfile;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.service.PicklistApplicationService;
import com.xmut.ebus.application.business.picklist.support.PicklistArtifactParser;
import com.xmut.ebus.application.business.picklist.support.PicklistParseResult;
import com.xmut.ebus.application.business.agent.support.CreditHoldSupport;
import com.xmut.ebus.application.business.agent.support.PicklistArtifactPersistPlugin;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.LegacyPicklistFallbackProjector;
import com.xmut.ebus.application.business.computer.NoSkillMarkdownProjector;
import com.xmut.ebus.application.business.computer.NormalizeViewProjector;
import com.xmut.ebus.application.business.computer.ViewProjectorChain;
import com.xmut.ebus.application.business.picklist.support.PicklistViewProjector;
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
import com.xmut.lims.pi.agent.skill.Skill;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock
    private PicklistArtifactParser picklistArtifactParser;
    @Mock
    private PicklistApplicationService picklistApplicationService;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        ViewProjectorChain chain = new ViewProjectorChain(java.util.Arrays.asList(
                new NormalizeViewProjector(),
                new LegacyPicklistFallbackProjector(new PicklistViewProjector()),
                new NoSkillMarkdownProjector()));
        service = new AgentApplicationService(
                new CreditHoldSupport(creditApplicationService),
                generationRunRepository,
                piSessionSceneRepository,
                sceneRepository,
                sceneCapabilityPackLoader,
                agentSession,
                java.util.Collections.<com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin>singletonList(
                        new PicklistArtifactPersistPlugin(picklistArtifactParser, picklistApplicationService)),
                new ComputerViewResolver(chain),
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
        assertEquals("ecommerce-picklist", SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID);
        assertEquals("ecommerce-picklist", promptCaptor.getValue().getSkillId());
        verify(agentSession).prompt(argThat(req ->
                "ecommerce-picklist".equals(req.getSkillId())));

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
                                "classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md"))));
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

    @Test
    void preparePicklistRunRejectsBlankText() {
        assertThrows(BusinessException.class, () -> service.preparePicklistRun(
                StartPicklistRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode(ECOM_SCENE_CODE)
                        .text("  ")
                        .build()));
        verify(creditApplicationService, never()).reserveOne(anyString());
    }

    @Test
    void prepareGenerationRunNoSkillRejectsBlankText() {
        assertThrows(BusinessException.class, () -> service.prepareGenerationRun(
                StartGenerationRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode(ECOM_SCENE_CODE)
                        .text("  ")
                        .dryRun(false)
                        .build()));
        verify(creditApplicationService, never()).reserveOne(anyString());
    }

    @Test
    void prepareGenerationRunBlankSkillIdUsesNoSkillProfile() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder()
                .userId(USER_ID)
                .sceneCode(ECOM_SCENE_CODE)
                .text("随便聊聊")
                .dryRun(false)
                .build());

        assertFalse(ctx.getProfile().isSkillBound());
        assertFalse(ctx.getProfile().isDryRun());
        assertEquals("随便聊聊", ctx.getPromptText());
        verify(creditApplicationService).reserveOne(USER_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamGenerationRunNoSkillSettlesOnUsableMarkdownView() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-ok", USER_ID, HOLD_ID, "session-ns-ok", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ns-ok", "session-ns-ok", "  这是一段草稿回复  ",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ns-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-ok", USER_ID, HOLD_ID, "session-ns-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.run_started, events.get(0).getName());
        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.artifact_ready)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertNotNull(ready.getData().get("view"));
        assertFalse(ready.getData().containsKey("artifactRef"));
        assertFalse(ready.getData().containsKey("artifactType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("draft", view.get("title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals(1, blocks.size());
        assertEquals("markdown", blocks.get(0).get("type"));
        assertEquals("这是一段草稿回复", blocks.get(0).get("text"));

        assertEquals(Ad4EventName.run_settled, events.get(events.size() - 1).getName());
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_failed));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(picklistApplicationService, never()).persistUsable(any());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals(null, promptCaptor.getValue().getSkillId());
        assertEquals("你好", promptCaptor.getValue().getText());
        ArgumentCaptor<GenerationRun> runCaptor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(runCaptor.capture());
        assertEquals(GenerationRunStatus.SETTLED, runCaptor.getValue().getStatus());
        assertEquals(null, runCaptor.getValue().getArtifactRef());
    }

    @Test
    void streamGenerationRunNoSkillSettleFailureDoesNotReleaseOrEmitReady() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-settle", USER_ID, HOLD_ID, "session-ns-settle", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ns-settle", "session-ns-settle", "草稿",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-ns-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-settle", USER_ID, HOLD_ID, "session-ns-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(AgentApplicationService.PICKLIST_SETTLE_FAILED, failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.artifact_ready));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_settled));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
    }

    @Test
    void streamGenerationRunNoSkillReleasesWithRunFailedWhenModelFails() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-fail", USER_ID, HOLD_ID, "session-ns-fail", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenReturn(TurnResult.failed("run-ns-fail", "模型超时"));
        when(generationRunRepository.findById("run-ns-fail")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-fail", USER_ID, HOLD_ID, "session-ns-fail",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.artifact_ready));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_settled));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void preparePicklistRunRejectsInsufficientCredit() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT, "积分不足，请升级套餐"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.preparePicklistRun(
                StartPicklistRunCommand.builder()
                        .userId(USER_ID)
                        .sceneCode(ECOM_SCENE_CODE)
                        .text("帮我选品")
                        .build()));
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamPicklistRunSettlesOnUsableArtifact() {
        PicklistRunContext ctx = picklistCtx("run-pl-ok", "session-pl-ok");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-ok", "session-pl-ok", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        PersistPicklistCommand persistCmd = PersistPicklistCommand.builder()
                .userId(USER_ID)
                .runId("run-pl-ok")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用电商知识推断，非实时平台数据")
                .items(Collections.emptyList())
                .build();
        when(picklistArtifactParser.parse(VALID_PICKLIST_JSON, USER_ID, "run-pl-ok"))
                .thenReturn(new PicklistParseResult(persistCmd, null));
        PicklistArtifactDTO artifact = sampleArtifact("pl-1", "run-pl-ok");
        when(picklistApplicationService.persistUsable(any(PersistPicklistCommand.class))).thenReturn(artifact);
        when(generationRunRepository.findById("run-pl-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-ok", USER_ID, HOLD_ID, "session-pl-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, events::add);

        assertEquals(Ad4EventName.run_started, events.get(0).getName());
        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.artifact_ready)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertEquals("picklist", ready.getData().get("artifactType"));
        assertEquals("pl-1", ready.getData().get("artifactRef"));
        assertEquals("pl-1", ready.getData().get("picklistId"));
        assertEquals("run-pl-ok", ready.getData().get("runId"));
        assertEquals("domestic-generic-default", ready.getData().get("templateId"));
        assertTrue(String.valueOf(ready.getData().get("disclaimer")).contains("非实时"));
        assertTrue(ready.getData().get("items") instanceof List);
        assertEquals(8, ((List<?>) ready.getData().get("items")).size());
        assertNotNull(ready.getData().get("view"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("picklist", view.get("title"));
        assertEquals(Ad4EventName.run_settled, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(GenerationRunStatus.SETTLED, captor.getValue().getStatus());
        assertEquals("pl-1", captor.getValue().getArtifactRef());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals(SceneCapabilityPackLoader.SKILL_PICKLIST, promptCaptor.getValue().getSkillId());
        assertEquals("帮我选品", promptCaptor.getValue().getText());
        ArgumentCaptor<PersistPicklistCommand> persistCaptor = ArgumentCaptor.forClass(PersistPicklistCommand.class);
        verify(picklistApplicationService).persistUsable(persistCaptor.capture());
        assertEquals(ECOM_SCENE_CODE, persistCaptor.getValue().getSceneCode());
    }

    @Test
    void streamPicklistRunPrefersSkillViewOverLegacyProjection() {
        PicklistRunContext ctx = picklistCtx("run-pl-view", "session-pl-view");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-view", "session-pl-view", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        PersistPicklistCommand persistCmd = PersistPicklistCommand.builder()
                .userId(USER_ID)
                .runId("run-pl-view")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用电商知识推断，非实时平台数据")
                .items(Collections.emptyList())
                .build();
        Map<String, Object> skillView = new LinkedHashMap<String, Object>();
        skillView.put("version", 1);
        skillView.put("title", "report");
        skillView.put("status", "ready");
        List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
        Map<String, Object> note = new LinkedHashMap<String, Object>();
        note.put("type", "note");
        note.put("tone", "mute");
        note.put("text", "skill-owned note");
        blocks.add(note);
        skillView.put("blocks", blocks);
        when(picklistArtifactParser.parse(VALID_PICKLIST_JSON, USER_ID, "run-pl-view"))
                .thenReturn(new PicklistParseResult(persistCmd, skillView));
        when(picklistApplicationService.persistUsable(any(PersistPicklistCommand.class)))
                .thenReturn(sampleArtifact("pl-view", "run-pl-view"));
        when(generationRunRepository.findById("run-pl-view")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-view", USER_ID, HOLD_ID, "session-pl-view",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, events::add);

        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.artifact_ready)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals("report", view.get("title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outBlocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals(1, outBlocks.size());
        assertEquals("note", outBlocks.get(0).get("type"));
        assertEquals("skill-owned note", outBlocks.get(0).get("text"));
    }

    @Test
    void streamPicklistRunReleasesWithoutSettleWhenViewGateFails() {
        ViewProjectorChain emptyChain = new ViewProjectorChain(Collections.<com.xmut.ebus.application.business.computer.ComputerViewProjector>emptyList());
        AgentApplicationService gated = new AgentApplicationService(
                new CreditHoldSupport(creditApplicationService),
                generationRunRepository,
                piSessionSceneRepository,
                sceneRepository,
                sceneCapabilityPackLoader,
                agentSession,
                java.util.Collections.<com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin>singletonList(
                        new PicklistArtifactPersistPlugin(picklistArtifactParser, picklistApplicationService)),
                new ComputerViewResolver(emptyChain),
                Clock.fixed(NOW, ZoneOffset.UTC));

        PicklistRunContext ctx = picklistCtx("run-pl-noview", "session-pl-noview");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-noview", "session-pl-noview", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        PersistPicklistCommand persistCmd = PersistPicklistCommand.builder()
                .userId(USER_ID)
                .runId("run-pl-noview")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用电商知识推断，非实时平台数据")
                .items(Collections.emptyList())
                .build();
        when(picklistArtifactParser.parse(VALID_PICKLIST_JSON, USER_ID, "run-pl-noview"))
                .thenReturn(new PicklistParseResult(persistCmd, null));
        when(picklistApplicationService.persistUsable(any(PersistPicklistCommand.class)))
                .thenReturn(sampleArtifact("pl-noview", "run-pl-noview"));
        when(generationRunRepository.findById("run-pl-noview")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-noview", USER_ID, HOLD_ID, "session-pl-noview",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        gated.streamPicklistRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.run_failed));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.artifact_ready));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_settled));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamPicklistRunSettleFailureDoesNotReleaseOrEmitArtifact() {
        PicklistRunContext ctx = picklistCtx("run-pl-settle", "session-pl-settle");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-settle", "session-pl-settle", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        PersistPicklistCommand persistCmd = PersistPicklistCommand.builder()
                .userId(USER_ID)
                .runId("run-pl-settle")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用电商知识推断，非实时平台数据")
                .items(Collections.emptyList())
                .build();
        when(picklistArtifactParser.parse(VALID_PICKLIST_JSON, USER_ID, "run-pl-settle"))
                .thenReturn(new PicklistParseResult(persistCmd, null));
        when(picklistApplicationService.persistUsable(any(PersistPicklistCommand.class)))
                .thenReturn(sampleArtifact("pl-s", "run-pl-settle"));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-pl-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-settle", USER_ID, HOLD_ID, "session-pl-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(AgentApplicationService.PICKLIST_SETTLE_FAILED, failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.artifact_ready));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_settled));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(GenerationRunStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void streamPicklistRunEmitFailureAfterSettleDoesNotMarkFailed() {
        PicklistRunContext ctx = picklistCtx("run-pl-emit", "session-pl-emit");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-emit", "session-pl-emit", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        PersistPicklistCommand persistCmd = PersistPicklistCommand.builder()
                .userId(USER_ID)
                .runId("run-pl-emit")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用电商知识推断，非实时平台数据")
                .items(Collections.emptyList())
                .build();
        when(picklistArtifactParser.parse(VALID_PICKLIST_JSON, USER_ID, "run-pl-emit"))
                .thenReturn(new PicklistParseResult(persistCmd, null));
        when(picklistApplicationService.persistUsable(any(PersistPicklistCommand.class)))
                .thenReturn(sampleArtifact("pl-e", "run-pl-emit"));
        when(generationRunRepository.findById("run-pl-emit")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-emit", USER_ID, HOLD_ID, "session-pl-emit",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, event -> {
            if (Ad4EventName.artifact_ready.equals(event.getName())) {
                throw new IllegalStateException("sse broken after settle");
            }
            events.add(event);
        });

        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(1, captor.getAllValues().size());
        assertEquals(GenerationRunStatus.SETTLED, captor.getValue().getStatus());
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_failed));
    }

    @Test
    void streamPicklistRunReleasesWhenArtifactUnusable() {
        PicklistRunContext ctx = picklistCtx("run-pl-bad", "session-pl-bad");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-bad", "session-pl-bad", "{bad}",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(picklistArtifactParser.parse("{bad}", USER_ID, "run-pl-bad"))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID, PicklistArtifactParser.MSG_UNUSABLE));
        when(generationRunRepository.findById("run-pl-bad")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-bad", USER_ID, HOLD_ID, "session-pl-bad",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals(PicklistArtifactParser.MSG_UNUSABLE, failed.getData().get("reason"));
        assertFalse(Boolean.TRUE.equals(failed.getData().get("emptyRun")));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(picklistApplicationService, never()).persistUsable(any());
    }

    @Test
    void streamPicklistRunReleasesWhenModelFails() {
        PicklistRunContext ctx = picklistCtx("run-pl-fail", "session-pl-fail");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenReturn(TurnResult.failed("run-pl-fail", "模型超时"));
        when(generationRunRepository.findById("run-pl-fail")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-fail", USER_ID, HOLD_ID, "session-pl-fail",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamPicklistRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.run_failed, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(picklistArtifactParser, never()).parse(anyString(), anyString(), anyString());
    }

    @Test
    void streamEmptyRunStillNeverSettles() {
        EmptyRunContext ctx = emptyCtx("run-empty-no-settle", "session-empty-no-settle");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-empty-no-settle", "session-empty-no-settle", "stub",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-empty-no-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-empty-no-settle", USER_ID, HOLD_ID, "session-empty-no-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamEmptyRun(ctx, events::add);

        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.artifact_ready));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.run_settled));
        assertEquals(Ad4EventName.run_failed, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    private static final String VALID_PICKLIST_JSON = "{\"ok\":true}";

    private PicklistRunContext picklistCtx(String runId, String sessionId) {
        return new PicklistRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE, "帮我选品");
    }

    private static PicklistArtifactDTO sampleArtifact(String picklistId, String runId) {
        List<PicklistArtifactDTO.PicklistItemDTO> items = new ArrayList<PicklistArtifactDTO.PicklistItemDTO>();
        for (int i = 0; i < 8; i++) {
            items.add(new PicklistArtifactDTO.PicklistItemDTO(
                    (i == 0 ? "【优先试】" : "") + "品" + i,
                    "19-39",
                    "台面积水",
                    "租房刚需",
                    "多色" + i,
                    "细分" + (i % 3),
                    "高｜需求", "中｜竞争", "中｜利润", "低｜风险"));
        }
        return new PicklistArtifactDTO(
                picklistId, runId, "domestic-generic-default",
                "基于通用电商知识推断，非实时平台数据", "默认假设", items);
    }

    private EmptyRunContext emptyCtx(String runId, String sessionId) {
        return new EmptyRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE);
    }

    private void stubEcommercePack() {
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
    }

    private static SceneCapabilityPack ecommercePack() {
        return new SceneCapabilityPack(ECOM_SCENE_CODE, Arrays.asList(
                skill(SceneCapabilityPackLoader.SKILL_PICKLIST,
                        "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md"),
                skill(SceneCapabilityPackLoader.SKILL_SKULIST,
                        "classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md")));
    }

    private static Skill skill(String id, String promptRef) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef(promptRef)
                .allowedTools(Collections.singletonList("read_skill"))
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
