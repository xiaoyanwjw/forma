package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.ResumeGenerationRunCommand;
import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.support.*;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.tool.AskHumanToolHandlerTest;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.business.computer.ComputerViewProjector;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.NoSkillMarkdownProjector;
import com.xmut.ebus.application.business.computer.NormalizeViewProjector;
import com.xmut.ebus.application.business.marketplace.SearchSkuToolHandler;
import com.xmut.ebus.application.business.media.support.ListingMediaMountSupport;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import com.xmut.ebus.application.business.session.query.SessionQueryService;
import com.xmut.ebus.common.util.StringUtils;
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
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.event.ToolSuspendPayload;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import com.xmut.lims.pi.agent.skill.Skill;
import com.xmut.lims.pi.ai.tool.ToolResult;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T05:00:00Z");
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String HOLD_ID = "22222222-2222-2222-2222-222222222222";
    private static final String EXEC_HOLD_ID = "33333333-3333-3333-3333-333333333333";
    private static final String ASK_CALL_ID = "call-ask-1";
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
    private ArtifactPersistPlugin artifactPersistPlugin;
    @Mock
    private Checkpointer checkpointer;

    private MediaStore mediaStore;
    private ListingMediaMountSupport listingMediaMountSupport;
    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(artifactPersistPlugin.persist(
                        anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap()))
                .thenReturn(new PersistedGenerationArtifact("art-1", Collections.<String, Object>emptyMap()));
        org.mockito.Mockito.lenient().when(checkpointer.loadLatest(anyString()))
                .thenReturn(Optional.of(new Checkpoint(
                        "cp-1", "run", 1, "tools", null, NOW, Collections.<String, Object>emptyMap())));
        mediaStore = new FakeMediaStore();
        listingMediaMountSupport = new ListingMediaMountSupport(mediaStore);
        service = newService(defaultViewResolver());
    }

    private AgentApplicationService newService(ComputerViewResolver viewResolver) {
        GenerationOutputParser parser =
                new GenerationOutputParser(new com.fasterxml.jackson.databind.ObjectMapper());
        SkuHitlInterceptor listingHitl = new SkuHitlInterceptor(
                new CreditHoldSupport(creditApplicationService),
                parser,
                artifactPersistPlugin,
                viewResolver,
                generationRunRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new AgentApplicationService(
                new CreditHoldSupport(creditApplicationService),
                generationRunRepository,
                piSessionSceneRepository,
                sceneRepository,
                sceneCapabilityPackLoader,
                agentSession,
                parser,
                artifactPersistPlugin,
                viewResolver,
                java.util.Arrays.<BilledRunInterceptor>asList(
                        listingHitl,
                        new SkuMediaMountInterceptor(listingMediaMountSupport)),
                java.util.Collections.<BilledRunListener>singletonList(listingHitl),
                java.util.Collections.<BilledSuspendedHandler>singletonList(listingHitl),
                checkpointer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ComputerViewResolver defaultViewResolver() {
        return new ComputerViewResolver(java.util.Arrays.asList(
                new NormalizeViewProjector(),
                new NoSkillMarkdownProjector()));
    }

    @Test
    void prepareDryGenerationRunReservesAndPersistsRunWithSceneByCode() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
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
        verify(piSessionSceneRepository).ensureBound(eq(ctx.getSessionId()), eq(ECOM_SCENE_ID), eq(ECOM_SCENE_CODE), eq(USER_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareDryGenerationRunSucceedsWithSceneIdOnly() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
                .build());

        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).save(captor.capture());
        assertEquals(ECOM_SCENE_ID, captor.getValue().getSceneId());
        assertEquals(ECOM_SCENE_CODE, captor.getValue().getSceneCode());
        verify(piSessionSceneRepository).ensureBound(eq(ctx.getSessionId()), eq(ECOM_SCENE_ID), eq(ECOM_SCENE_CODE), eq(USER_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareDryGenerationRunSucceedsWhenSceneIdAndCodeConsistent() {
        Scene ecommerce = ecommerceScene();
        when(sceneRepository.findByBizId(ECOM_SCENE_ID)).thenReturn(Optional.of(ecommerce));
        when(sceneRepository.findBySceneCode(ECOM_SCENE_CODE)).thenReturn(Optional.of(ecommerce));
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        verify(generationRunRepository).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareDryGenerationRunRejectsMissingSceneWithoutReserveOrSave() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true).userId(USER_ID).build()));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals(AgentApplicationService.MSG_SCENE_REQUIRED, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).ensureBound(anyString(), anyString(), anyString(), anyString());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareDryGenerationRunRejectsUnknownSceneCode() {
        when(sceneRepository.findBySceneCode("unknown")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sceneCode("unknown")
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_FOUND, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareDryGenerationRunRejectsUnknownSceneId() {
        when(sceneRepository.findByBizId("missing-id")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sceneId("missing-id")
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_FOUND, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareDryGenerationRunRejectsConflictingSceneIdAndCode() {
        when(sceneRepository.findByBizId(ECOM_SCENE_ID)).thenReturn(Optional.of(ecommerceScene()));
        when(sceneRepository.findBySceneCode(GRAY_SCENE_CODE)).thenReturn(Optional.of(grayScene()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sceneId(ECOM_SCENE_ID)
                        .sceneCode(GRAY_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_MISMATCH, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareDryGenerationRunRejectsComingSoonScene() {
        when(sceneRepository.findBySceneCode(GRAY_SCENE_CODE)).thenReturn(Optional.of(grayScene()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sceneCode(GRAY_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_OPEN, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareDryGenerationRunRejectsSessionBoundToOtherScene() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("fixed-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session", GRAY_SCENE_ID, GRAY_SCENE_CODE)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sessionId("fixed-session")
                        .sceneCode(ECOM_SCENE_CODE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SESSION_SCENE_MISMATCH, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).ensureBound(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void prepareDryGenerationRunWritesSceneWhenSessionHasNoneYet() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("legacy-session"))
                .thenReturn(Optional.of(new SessionSceneBinding("legacy-session", null, null)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                .userId(USER_ID)
                .sessionId("legacy-session")
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        verify(piSessionSceneRepository).ensureBound("legacy-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID);
    }

    @Test
    void prepareGenerationRunForbiddenWhenSessionOwnedByOtherUser() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("foreign-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "foreign-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, "other-user")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sessionId("foreign-session")
                        .sceneCode(ECOM_SCENE_CODE)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals(SessionQueryService.MSG_UNAVAILABLE, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).ensureBound(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void prepareGenerationRunAllowsOwnSessionAndStillEnsureBound() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("own-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "own-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                .userId(USER_ID)
                .sessionId("own-session")
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        assertEquals("own-session", ctx.getSessionId());
        verify(piSessionSceneRepository).ensureBound("own-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID);
        verify(creditApplicationService).reserveOne(USER_ID);
    }

    @Test
    void prepareDryGenerationRunStoresFixedSessionIdAndNewHoldEachTime() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId("fixed-session-id"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session-id", ECOM_SCENE_ID, ECOM_SCENE_CODE)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn("hold-a", "hold-b");
        String sessionId = "fixed-session-id";

        GenerationRunContext first = service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                .userId(USER_ID)
                .sessionId(sessionId)
                .sceneCode(ECOM_SCENE_CODE)
                .build());
        GenerationRunContext second = service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
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
    void prepareDryGenerationRunDoesNotCreateRunWhenInsufficient() {
        stubEcommerceByCode();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().dryRun(true)
                        .userId(USER_ID)
                        .sceneCode(ECOM_SCENE_CODE)
                        .build()));
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunEmitsStartedDeltaToRunFailedAndReleasesWithoutSettle() {
        GenerationRunContext ctx = emptyCtx("run-1", "session-1");
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
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent started = events.get(0);
        assertEquals(Ad4EventName.RUN_STARTED, started.getName());
        assertEquals("run-1", started.getData().get("runId"));
        assertEquals(HOLD_ID, started.getData().get("holdId"));
        assertEquals("session-1", started.getData().get("sessionId"));

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
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
    void streamEmptyRunEndsFailedWithoutSynthesizingMessageDelta() {
        GenerationRunContext ctx = emptyCtx("run-3", "session-3");
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
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.MESSAGE_DELTA));
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        assertEquals(AgentApplicationService.EMPTY_RUN_FAIL_REASON, failed.getData().get("reason"));
    }

    @Test
    void streamEmptyRunReleasesOnAgentFailureAndStillDoesNotSettle() {
        GenerationRunContext ctx = emptyCtx("run-2", "session-2");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenThrow(new RuntimeException("agent boom"));
        when(generationRunRepository.findById("run-2")).thenReturn(Optional.of(
                GenerationRun.start("run-2", USER_ID, HOLD_ID, "session-2",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_STARTED, events.get(0).getName());
        assertEquals(Ad4EventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(eq(USER_ID), eq(HOLD_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunReportsReleaseFailureReasonAndDoesNotClaimReleased() {
        GenerationRunContext ctx = emptyCtx("run-4", "session-4");
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
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        assertEquals(AgentApplicationService.RELEASE_FAILED_REASON, failed.getData().get("reason"));
        assertFalse(String.valueOf(failed.getData().get("reason")).contains("预占已释放"));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunAbortsOnSinkFailureThenReleasesAndEmitsRunToRunFailed() {
        GenerationRunContext ctx = emptyCtx("run-5", "session-5");
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
        service.streamGenerationRun(ctx, event -> {
            int n = accepts.incrementAndGet();
            if (n == 2) {
                throw new IllegalStateException("sse broken");
            }
            events.add(event);
        });

        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SSE_SEND_FAILED_RELEASED, failed.getData().get("reason"));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunFailsHumanWithoutPromptWhenPackMissing() {
        GenerationRunContext ctx = emptyCtx("run-pack-miss", "session-pack-miss");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID,
                        SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE));
        when(generationRunRepository.findById("run-pack-miss")).thenReturn(Optional.of(
                GenerationRun.start("run-pack-miss", USER_ID, HOLD_ID, "session-pack-miss",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_STARTED, events.get(0).getName());
        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(agentSession, never()).subscribe(any());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunFailsHumanWithoutPromptWhenDefaultSkillMissing() {
        GenerationRunContext ctx = emptyCtx("run-default-miss", "session-default-miss");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(
                new SceneCapabilityPack(ECOM_SCENE_CODE, Collections.singletonList(
                        skill(SceneCapabilityPackLoader.SKILL_SKULIST,
                                "classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md"))));
        when(generationRunRepository.findById("run-default-miss")).thenReturn(Optional.of(
                GenerationRun.start("run-default-miss", USER_ID, HOLD_ID, "session-default-miss",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(agentSession, never()).subscribe(any());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamEmptyRunReportsReleaseFailureWhenPackMissing() {
        GenerationRunContext ctx = emptyCtx("run-pack-miss-release", "session-pack-miss-release");
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID,
                        SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE));
        when(generationRunRepository.findById("run-pack-miss-release")).thenReturn(Optional.of(
                GenerationRun.start("run-pack-miss-release", USER_ID, HOLD_ID, "session-pack-miss-release",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.RELEASE_FAILED_REASON, failed.getData().get("reason"));
        assertEquals(Boolean.TRUE, failed.getData().get("emptyRun"));
        verify(agentSession, never()).prompt(any(PromptRequest.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
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

        assertEquals(Ad4EventName.RUN_STARTED, events.get(0).getName());
        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.ARTIFACT_READY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertNotNull(ready.getData().get("view"));
        assertTrue(StringUtils.hasText((String) ready.getData().get("artifactRef")));
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

        assertEquals(Ad4EventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-ns-ok"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_NONE), anyMap(), anyMap());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals(null, promptCaptor.getValue().getSkillId());
        assertEquals("你好", promptCaptor.getValue().getText());
        ArgumentCaptor<GenerationRun> runCaptor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(runCaptor.capture());
        assertEquals(GenerationRunStatus.SETTLED, runCaptor.getValue().getStatus());
        assertTrue(StringUtils.hasText(runCaptor.getValue().getArtifactRef()));
    }

    @Test
    void streamGenerationRun_whenViewUnavailable_doesNotPersistOrSettle() {
        AgentApplicationService gated = newService(new ComputerViewResolver(
                Collections.<ComputerViewProjector>emptyList()));
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-noview", USER_ID, HOLD_ID, "session-ns-noview", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ns-noview", "session-ns-noview", "草稿",
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ns-noview")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-noview", USER_ID, HOLD_ID, "session-ns-noview",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        gated.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
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
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
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
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamPicklistRunSettlesWithoutSearchSkuToolEvent_skillSoftConstraintOnly() {
        // App layer no longer hard-gates ≥1 search_sku; Skill soft rules remain in SKILL.md.
        GenerationRunContext ctx = picklistCtx("run-pl-nosearch", "session-pl-nosearch");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-pl-nosearch", "session-pl-nosearch", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-pl-nosearch")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-nosearch", USER_ID, HOLD_ID, "session-pl-nosearch",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-nosearch"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_PICKLIST), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunSettlesWhenSearchSkuSucceeded() {
        GenerationRunContext ctx = picklistCtx("run-pl-searchok", "session-pl-searchok");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-searchok", "session-pl-searchok", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-searchok")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-searchok", USER_ID, HOLD_ID, "session-pl-searchok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-searchok"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_PICKLIST), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunSettlesOnUsableArtifact() {
        GenerationRunContext ctx = picklistCtx("run-pl-ok", "session-pl-ok");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-ok", "session-pl-ok", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-ok", USER_ID, HOLD_ID, "session-pl-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_STARTED, events.get(0).getName());
        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.ARTIFACT_READY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertEquals("art-1", ready.getData().get("artifactRef"));
        assertFalse(ready.getData().containsKey("items"));
        assertNotNull(ready.getData().get("view"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("picklist", view.get("title"));
        assertEquals(Ad4EventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(GenerationRunStatus.SETTLED, captor.getValue().getStatus());
        assertEquals("art-1", captor.getValue().getArtifactRef());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals(SceneCapabilityPackLoader.SKILL_PICKLIST, promptCaptor.getValue().getSkillId());
        assertEquals("帮我选品", promptCaptor.getValue().getText());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-ok"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_PICKLIST), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunPrefersSkillViewOverLegacyProjection() {
        GenerationRunContext ctx = picklistCtx("run-pl-view", "session-pl-view");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-view", "session-pl-view", SKILL_OWNED_VIEW_JSON);
        when(generationRunRepository.findById("run-pl-view")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-view", USER_ID, HOLD_ID, "session-pl-view",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.ARTIFACT_READY)
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
        AgentApplicationService gated = newService(new ComputerViewResolver(
                Collections.<ComputerViewProjector>emptyList()));

        GenerationRunContext ctx = picklistCtx("run-pl-noview", "session-pl-noview");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-noview", "session-pl-noview", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-noview")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-noview", USER_ID, HOLD_ID, "session-pl-noview",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        gated.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunSettleFailureDoesNotReleaseOrEmitArtifact() {
        GenerationRunContext ctx = picklistCtx("run-pl-settle", "session-pl-settle");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-settle", "session-pl-settle", VALID_PICKLIST_JSON);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-pl-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-settle", USER_ID, HOLD_ID, "session-pl-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(GenerationRunStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void streamPicklistRunEmitFailureAfterSettleDoesNotMarkFailed() {
        GenerationRunContext ctx = picklistCtx("run-pl-emit", "session-pl-emit");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-emit", "session-pl-emit", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-emit")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-emit", USER_ID, HOLD_ID, "session-pl-emit",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, event -> {
            if (Ad4EventName.ARTIFACT_READY.equals(event.getName())) {
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
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
    }

    @Test
    void streamPicklistRunReleasesWhenArtifactUnusable() {
        ComputerViewResolver failingView = org.mockito.Mockito.mock(ComputerViewResolver.class);
        when(failingView.resolve(any())).thenThrow(
                new BusinessException(ErrorCode.PARAM_INVALID, ComputerViewResolver.MSG_VIEW_UNAVAILABLE));
        AgentApplicationService gated = newService(failingView);

        GenerationRunContext ctx = picklistCtx("run-pl-bad", "session-pl-bad");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-bad", "session-pl-bad", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-bad")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-bad", USER_ID, HOLD_ID, "session-pl-bad",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        gated.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(ComputerViewResolver.MSG_VIEW_UNAVAILABLE, failed.getData().get("reason"));
        assertFalse(Boolean.TRUE.equals(failed.getData().get("emptyRun")));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunReleasesWhenModelFails() {
        GenerationRunContext ctx = picklistCtx("run-pl-fail", "session-pl-fail");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenReturn(TurnResult.failed("run-pl-fail", "模型超时"));
        when(generationRunRepository.findById("run-pl-fail")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-fail", USER_ID, HOLD_ID, "session-pl-fail",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void streamEmptyRunStillNeverSettles() {
        GenerationRunContext ctx = emptyCtx("run-empty-no-settle", "session-empty-no-settle");
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
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        assertEquals(Ad4EventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamListingRunMountsPlaceholderAndSettles() {
        GenerationRunContext ctx = listingCtx("run-listing-ok", "session-listing-ok");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-ok", "session-listing-ok", VALID_LISTING_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-listing-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-ok", USER_ID, HOLD_ID, "session-listing-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-ok"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_SKU), anyMap(),
                argThat(payload -> {
                    Object ids = payload.get("mediaObjectIds");
                    return ids instanceof List && !((List<?>) ids).isEmpty();
                }));
        Ad4SseEvent ready = events.stream()
                .filter(e -> e.getName() == Ad4EventName.ARTIFACT_READY)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertNotNull(view);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertTrue(blocks.stream().anyMatch(b ->
                "media".equals(b.get("type"))
                        && b.get("mediaObjectId") != null
                        && b.get("src") != null));
    }

    @Test
    void streamListingRunReleasesWhenMediaStoreFails() {
        ((FakeMediaStore) mediaStore).failPuts = true;
        GenerationRunContext ctx = listingCtx("run-listing-media-fail", "session-listing-media-fail");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-media-fail", "session-listing-media-fail", VALID_LISTING_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-listing-media-fail")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-media-fail", USER_ID, HOLD_ID, "session-listing-media-fail",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_FAILED, events.get(events.size() - 1).getName());
        assertTrue(String.valueOf(events.get(events.size() - 1).getData().get("reason")).contains("服务繁忙"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void streamListingRunReleasesWhenSkuPayloadUnusable() {
        when(artifactPersistPlugin.persist(anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID, ArtifactPersistPlugin.MSG_SKU_UNUSABLE));
        GenerationRunContext ctx = listingCtx("run-listing-bad", "session-listing-bad");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-bad", "session-listing-bad", VALID_LISTING_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-listing-bad")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-bad", USER_ID, HOLD_ID, "session-listing-bad",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamListingRunSettleFailureEmitsListingSettleFailed() {
        GenerationRunContext ctx = listingCtx("run-listing-settle", "session-listing-settle");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-settle", "session-listing-settle", VALID_LISTING_JSON,
                        Collections.<com.xmut.lims.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-listing-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-settle", USER_ID, HOLD_ID, "session-listing-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        Ad4SseEvent failed = events.get(events.size() - 1);
        assertEquals(Ad4EventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
    }

    @Test
    void streamListingRunReleasesWhenPromptFails() {
        GenerationRunContext ctx = listingCtx("run-listing-fail", "session-listing-fail");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "model down"));
        when(generationRunRepository.findById("run-listing-fail")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-fail", USER_ID, HOLD_ID, "session-listing-fail",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertEquals(Ad4EventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void listing_planGate_settlesOnce_thenHumanInputRequired() {
        GenerationRunContext ctx = listingCtx("run-listing-plan", "session-listing-plan");
        stubEcommercePack();
        stubListingAskHumanSuspend("run-listing-plan", "session-listing-plan", VALID_PLAN_JSON, ASK_CALL_ID);

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.HUMAN_INPUT_REQUIRED));
        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        verify(creditApplicationService, times(1)).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-plan"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_LISTING_PLAN), anyMap(), anyMap());
        assertTrue(ctx.isSettledOnSuspended());
    }

    @Test
    void listing_chunkedPlanDeltas_thenAskHuman_persistsUsablePlan() {
        GenerationRunContext ctx = listingCtx("run-listing-chunks", "session-listing-chunks");
        stubEcommercePack();
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            Consumer<PiEvent> listener = subscriber.get();
            int mid = VALID_PLAN_JSON.length() / 2;
            listener.accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, VALID_PLAN_JSON.substring(0, mid)));
            listener.accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, VALID_PLAN_JSON.substring(mid)));
            listener.accept(PiEvent.of(PiEventType.SUSPENDED,
                    ToolSuspendPayload.of(AskHumanToolHandlerTest.listingAskCall(ASK_CALL_ID),
                            "run-listing-chunks", "ask_human")));
            return TurnResult.builder()
                    .runId("run-listing-chunks")
                    .sessionId("session-listing-chunks")
                    .status(TurnResult.Status.SUSPENDED)
                    .finalResponse(VALID_PLAN_JSON)
                    .messages(Collections.<com.xmut.lims.pi.ai.message.Message>emptyList())
                    .build();
        });

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-chunks"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_LISTING_PLAN), anyMap(), anyMap());
        assertTrue(ctx.isSettledOnSuspended());
    }

    @Test
    void listing_askHumanWithoutStreamDeltas_usesTurnFinalResponseAsPlan() {
        GenerationRunContext ctx = listingCtx("run-listing-final", "session-listing-final");
        stubEcommercePack();
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            subscriber.get().accept(PiEvent.of(PiEventType.SUSPENDED,
                    ToolSuspendPayload.of(AskHumanToolHandlerTest.listingAskCall(ASK_CALL_ID),
                            "run-listing-final", "ask_human")));
            return TurnResult.builder()
                    .runId("run-listing-final")
                    .sessionId("session-listing-final")
                    .status(TurnResult.Status.SUSPENDED)
                    .finalResponse(VALID_PLAN_JSON)
                    .messages(Collections.<com.xmut.lims.pi.ai.message.Message>emptyList())
                    .build();
        });

        List<Ad4SseEvent> events = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, events::add);

        assertTrue(events.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_FAILED));
        assertTrue(events.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-final"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_LISTING_PLAN), anyMap(), anyMap());
    }

    @Test
    void prepareResume_withoutCheckpoint_rejects() {
        GenerationRun run = GenerationRun.start("run-no-cp", USER_ID, HOLD_ID,
                "session-no-cp", ECOM_SCENE_ID, ECOM_SCENE_CODE,
                SceneCapabilityPackLoader.SKILL_SKULIST, NOW);
        when(generationRunRepository.findById("run-no-cp")).thenReturn(Optional.of(run));
        when(checkpointer.loadLatest("run-no-cp")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.prepareResumeGenerationRun(ResumeGenerationRunCommand.builder()
                        .userId(USER_ID)
                        .runId("run-no-cp")
                        .toolCallId(ASK_CALL_ID)
                        .optionId(ListingHitlOptions.CONFIRM_EXECUTE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_RESUME_NOT_AWAITING, ex.getMessage());
    }

    @Test
    void listing_confirm_reservesExec_andSettlesSku() {
        GenerationRunContext ctx = listingCtx("run-listing-confirm", "session-listing-confirm");
        GenerationRun run = GenerationRun.start("run-listing-confirm", USER_ID, HOLD_ID,
                "session-listing-confirm", ECOM_SCENE_ID, ECOM_SCENE_CODE,
                SceneCapabilityPackLoader.SKILL_SKULIST, NOW);
        when(generationRunRepository.findById("run-listing-confirm")).thenReturn(Optional.of(run));
        stubEcommercePack();
        stubListingAskHumanSuspend("run-listing-confirm", "session-listing-confirm",
                VALID_PLAN_JSON, ASK_CALL_ID);

        List<Ad4SseEvent> first = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, first::add);
        assertTrue(ctx.isSettledOnSuspended());

        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(EXEC_HOLD_ID);
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.resume(any(ResumeRequest.class))).thenAnswer(invocation -> {
            ResumeRequest req = invocation.getArgument(0);
            assertEquals(ASK_CALL_ID, req.getToolCallId());
            assertTrue(req.getHumanInput().contains(ListingHitlOptions.CONFIRM_EXECUTE));
            return TurnResult.ok("run-listing-confirm", "session-listing-confirm", VALID_LISTING_JSON,
                    Collections.<com.xmut.lims.pi.ai.message.Message>emptyList());
        });

        List<Ad4SseEvent> second = new ArrayList<Ad4SseEvent>();
        service.resumeBilledRun(ResumeGenerationRunCommand.builder()
                .userId(USER_ID)
                .runId("run-listing-confirm")
                .toolCallId(ASK_CALL_ID)
                .optionId(ListingHitlOptions.CONFIRM_EXECUTE)
                .build(), second::add);

        assertTrue(second.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(second.stream().anyMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService).reserveOne(USER_ID);
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService).settle(USER_ID, EXEC_HOLD_ID);
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-confirm"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_SKU), anyMap(), anyMap());
        assertEquals(GenerationRunStatus.SETTLED, run.getStatus());
    }

    @Test
    void listing_supplement_doesNotSettleExec() {
        GenerationRunContext ctx = listingCtx("run-listing-supp", "session-listing-supp");
        GenerationRun run = GenerationRun.start("run-listing-supp", USER_ID, HOLD_ID,
                "session-listing-supp", ECOM_SCENE_ID, ECOM_SCENE_CODE,
                SceneCapabilityPackLoader.SKILL_SKULIST, NOW);
        when(generationRunRepository.findById("run-listing-supp")).thenReturn(Optional.of(run));
        stubEcommercePack();
        stubListingAskHumanSuspend("run-listing-supp", "session-listing-supp",
                VALID_PLAN_JSON, ASK_CALL_ID);

        List<Ad4SseEvent> first = new ArrayList<Ad4SseEvent>();
        service.streamGenerationRun(ctx, first::add);

        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.resume(any(ResumeRequest.class))).thenAnswer(invocation -> {
            Consumer<PiEvent> listener = subscriber.get();
            listener.accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, VALID_PLAN_JSON_SUPPLEMENT));
            listener.accept(PiEvent.of(PiEventType.SUSPENDED,
                    ToolSuspendPayload.of(AskHumanToolHandlerTest.listingAskCall("call-ask-2"),
                            "run-listing-supp", "ask_human")));
            return TurnResult.builder()
                    .runId("run-listing-supp")
                    .sessionId("session-listing-supp")
                    .status(TurnResult.Status.SUSPENDED)
                    .finalResponse("suspended at node: tools")
                    .messages(Collections.<com.xmut.lims.pi.ai.message.Message>emptyList())
                    .build();
        });

        List<Ad4SseEvent> second = new ArrayList<Ad4SseEvent>();
        service.resumeBilledRun(ResumeGenerationRunCommand.builder()
                .userId(USER_ID)
                .runId("run-listing-supp")
                .toolCallId(ASK_CALL_ID)
                .optionId(ListingHitlOptions.SUPPLEMENT)
                .freeText("主图再突出颜色")
                .build(), second::add);

        assertTrue(second.stream().anyMatch(e -> e.getName() == Ad4EventName.HUMAN_INPUT_REQUIRED));
        assertTrue(second.stream().anyMatch(e -> e.getName() == Ad4EventName.ARTIFACT_READY));
        assertTrue(second.stream().noneMatch(e -> e.getName() == Ad4EventName.RUN_SETTLED));
        verify(creditApplicationService, times(1)).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(creditApplicationService, never()).settle(eq(USER_ID), eq(EXEC_HOLD_ID));
        // 补充改策划：同 Run 覆盖写 listing_plan；策划 hold 仍只 settle 一次
        verify(artifactPersistPlugin, times(2)).persist(
                eq(USER_ID), eq("run-listing-supp"), eq(ECOM_SCENE_CODE),
                eq(SkillRunProfile.PERSIST_LISTING_PLAN), anyMap(), anyMap());
        assertEquals(GenerationRunStatus.RUNNING, run.getStatus());
    }

    private void stubListingAskHumanSuspend(String runId, String sessionId, String planJson, String callId) {
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            Consumer<PiEvent> listener = subscriber.get();
            listener.accept(PiEvent.of(PiEventType.MESSAGE_UPDATE, planJson));
            listener.accept(PiEvent.of(PiEventType.SUSPENDED,
                    ToolSuspendPayload.of(AskHumanToolHandlerTest.listingAskCall(callId), runId, "ask_human")));
            return TurnResult.builder()
                    .runId(runId)
                    .sessionId(sessionId)
                    .status(TurnResult.Status.SUSPENDED)
                    .finalResponse("suspended at node: tools")
                    .messages(Collections.<com.xmut.lims.pi.ai.message.Message>emptyList())
                    .build();
        });
    }

    private static final String VALID_PICKLIST_JSON =
            "{\"view\":{\"version\":1,\"title\":\"picklist\",\"status\":\"ready\","
                    + "\"blocks\":[{\"type\":\"note\",\"tone\":\"mute\",\"text\":\"ok\"}]},"
                    + "\"artifact\":{\"ok\":true}}";

    private static final String SKILL_OWNED_VIEW_JSON =
            "{\"view\":{\"version\":1,\"title\":\"report\",\"status\":\"ready\","
                    + "\"blocks\":[{\"type\":\"note\",\"tone\":\"mute\",\"text\":\"skill-owned note\"}]},"
                    + "\"artifact\":{\"ok\":true}}";

    private static final String VALID_LISTING_JSON =
            "{\"view\":{\"version\":1,\"title\":\"硅胶沥水垫 · 上架素材\",\"status\":\"ready\","
                    + "\"blocks\":["
                    + "{\"type\":\"media\",\"role\":\"hero\",\"placeholder\":\"白底主图方案\",\"alt\":\"主图\"},"
                    + "{\"type\":\"section\",\"heading\":\"详情标题\",\"body\":\"厨房硅胶沥水垫\"},"
                    + "{\"type\":\"section\",\"heading\":\"详情正文\",\"body\":\"易清洗防滑\"},"
                    + "{\"type\":\"section\",\"heading\":\"展示说明\",\"body\":\"主图突出颜色\",\"tone\":\"mute\"}"
                    + "]},"
                    + "\"artifact\":{\"title\":\"硅胶沥水垫 · 上架素材\","
                    + "\"templateId\":\"domestic-generic-default\","
                    + "\"heroPlan\":\"白底俯拍\",\"detailTitle\":\"厨房硅胶沥水垫\","
                    + "\"detailBody\":\"易清洗防滑\",\"displayNotes\":\"主图突出颜色\","
                    + "\"mediaObjectIds\":[]}}";

    private static final String VALID_PLAN_JSON =
            "{\"view\":{\"version\":1,\"title\":\"硅胶沥水垫 · 策划分镜\",\"status\":\"ready\","
                    + "\"blocks\":[{\"type\":\"markdown\",\"text\":"
                    + "\"## 成交方向\\n痛点驱动成交\\n\\n## 主图分镜\\n1. 白底主图\\n2. 使用场景\\n3. 细节特写\\n\\n"
                    + "## 标题草稿\\n厨房硅胶沥水垫\\n\\n## 详情大纲\\n1. 卖点清洗\\n2. 防滑结构\\n3. 场景搭配\"}]},"
                    + "\"artifact\":{\"title\":\"硅胶沥水垫 · 策划分镜\","
                    + "\"templateId\":\"domestic-generic-default\","
                    + "\"driver\":\"痛点驱动成交\","
                    + "\"frames\":[\"白底主图\",\"使用场景\",\"细节特写\"],"
                    + "\"modules\":[\"卖点清洗\",\"防滑结构\",\"场景搭配\"],"
                    + "\"titleDraft\":\"厨房硅胶沥水垫\"}}";

    private static final String VALID_PLAN_JSON_SUPPLEMENT =
            "{\"view\":{\"version\":1,\"title\":\"硅胶沥水垫 · 策划分镜\",\"status\":\"ready\","
                    + "\"blocks\":[{\"type\":\"markdown\",\"text\":"
                    + "\"## 成交方向\\n颜色优先成交\\n\\n## 主图分镜\\n1. 色块主图\\n2. 对比图\\n3. 场景图\\n\\n"
                    + "## 标题草稿\\n厨房硅胶沥水垫 高颜值\\n\\n## 详情大纲\\n1. 卖点清洗\\n2. 防滑结构\\n3. 场景搭配\"}]},"
                    + "\"artifact\":{\"title\":\"硅胶沥水垫 · 策划分镜\","
                    + "\"templateId\":\"domestic-generic-default\","
                    + "\"driver\":\"颜色优先成交\","
                    + "\"frames\":[\"色块主图\",\"对比图\",\"场景图\"],"
                    + "\"modules\":[\"卖点清洗\",\"防滑结构\",\"场景搭配\"],"
                    + "\"titleDraft\":\"厨房硅胶沥水垫 高颜值\"}}";

    private GenerationRunContext picklistCtx(String runId, String sessionId) {
        return new GenerationRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE,
                "帮我选品", SkillRunProfile.billedPicklist());
    }

    private GenerationRunContext listingCtx(String runId, String sessionId) {
        return new GenerationRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE,
                "请为硅胶沥水垫生成上架素材", SkillRunProfile.billedListing());
    }

    private GenerationRunContext emptyCtx(String runId, String sessionId) {
        return new GenerationRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE,
                "empty-run", SkillRunProfile.dry(null));
    }

    private static final class FakeMediaStore implements MediaStore {
        boolean failPuts;
        private final java.util.concurrent.ConcurrentHashMap<String, MediaObject> byId =
                new java.util.concurrent.ConcurrentHashMap<String, MediaObject>();

        @Override
        public MediaObject put(String userId, String contentType, byte[] bytes) {
            if (failPuts) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, ListingMediaMountSupport.MSG_MEDIA_BUSY);
            }
            String id = java.util.UUID.randomUUID().toString();
            MediaObject media = MediaObject.create(
                    id, userId, "memory/" + id, contentType, bytes == null ? 0 : bytes.length, NOW);
            byId.put(id, media);
            return media;
        }

        @Override
        public String issueReadUrl(String mediaObjectId) {
            if (!byId.containsKey(mediaObjectId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "媒体不存在");
            }
            return "data:image/png;base64,AAAA";
        }

        @Override
        public Optional<MediaObject> findById(String mediaObjectId) {
            return Optional.ofNullable(byId.get(mediaObjectId));
        }

        @Override
        public void delete(String mediaObjectId) {
            if (mediaObjectId != null) {
                byId.remove(mediaObjectId);
            }
        }
    }

    private void stubSubscribeEmittingSearchSkuOk(String runId, String sessionId, String finalText) {
        stubSubscribeEmittingSearchSkuEnd(runId, sessionId, finalText,
                ToolResult.ok("call-sku", SearchSkuToolHandler.TOOL_NAME, "[{}]"));
    }

    private void stubSubscribeEmittingSearchSkuEnd(String runId, String sessionId, String finalText,
                                                   ToolResult toolEnd) {
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            subscriber.get().accept(PiEvent.of(PiEventType.TOOL_EXECUTION_END, toolEnd));
            return TurnResult.ok(runId, sessionId, finalText,
                    Collections.<com.xmut.lims.pi.ai.message.Message>emptyList());
        });
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
