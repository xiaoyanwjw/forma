package com.xmut.forma.application.business.agent.service;

import com.xmut.forma.application.business.agent.command.ResumeGenerationRunCommand;
import com.xmut.forma.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.forma.application.business.agent.dto.GenerationRunContext;
import com.xmut.forma.application.business.agent.support.*;
import com.xmut.forma.application.business.agent.sse.SseEventName;
import com.xmut.forma.application.business.agent.workspace.RunWorkspaceService;
import com.xmut.forma.application.business.agent.sse.SseEvent;
import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.application.business.computer.ComputerViewProjector;
import com.xmut.forma.application.business.computer.ComputerViewResolver;
import com.xmut.forma.application.business.computer.NoSkillMarkdownProjector;
import com.xmut.forma.application.business.computer.NormalizeViewProjector;
import com.xmut.forma.application.business.session.query.SessionQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.common.output.RunAttachProvider;
import com.xmut.forma.extension.output.CatalogRunAttachmentProvider;
import com.xmut.forma.extension.output.TurnDeliverableKeys;
import com.xmut.forma.extension.output.WorkspaceOutputParser;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.forma.domain.business.agent.model.GenerationRun;
import com.xmut.forma.domain.business.agent.model.SessionSceneBinding;
import com.xmut.forma.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.forma.domain.business.agent.repository.PiSessionSceneRepository;
import com.xmut.forma.domain.business.scene.constant.SceneStatus;
import com.xmut.forma.domain.business.scene.model.Scene;
import com.xmut.forma.domain.business.scene.repository.SceneRepository;
import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.forma.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.forma.pi.agent.session.AgentSession;
import com.xmut.forma.pi.agent.session.PromptRequest;
import com.xmut.forma.pi.agent.session.TurnResult;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
    private com.xmut.forma.pi.agent.skill.SkillCatalog skillCatalog;
    @Mock
    private AgentSession agentSession;
    @Mock
    private ArtifactPersistPlugin artifactPersistPlugin;
    @Mock
    private Checkpointer checkpointer;
    @Mock
    private RunWorkspaceService runWorkspaceService;

    @TempDir
    Path tempWorkspace;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.lenient().when(artifactPersistPlugin.persist(
                        anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap()))
                .thenReturn(new PersistedGenerationArtifact("art-1", Collections.<String, Object>emptyMap()));
        org.mockito.Mockito.lenient().when(checkpointer.loadLatest(anyString()))
                .thenReturn(Optional.of(new Checkpoint(
                        "cp-1", "run", 1, "tools", null, NOW, Collections.<String, Object>emptyMap())));
        org.mockito.Mockito.lenient().when(runWorkspaceService.ensureRunDir(anyString(), anyString()))
                .thenReturn(tempWorkspace);
        org.mockito.Mockito.lenient().when(runWorkspaceService.runDir(anyString(), anyString()))
                .thenReturn(tempWorkspace);
        org.mockito.Mockito.lenient().when(skillCatalog.resolve(anyString())).thenAnswer(inv ->
                Optional.of(catalogSkill(inv.getArgument(0))));
        org.mockito.Mockito.lenient().when(skillCatalog.get(anyString())).thenAnswer(inv ->
                Optional.of(catalogSkill(inv.getArgument(0))));
        service = newService(defaultViewResolver());
        writeEnvelope("view.json", "artifact.json", VALID_PICKLIST_JSON);
    }

    private AgentApplicationService newService(ComputerViewResolver viewResolver) {
        GenerationOutputParser fallback = new GenerationOutputParser();
        OutputParserComposite composite = new OutputParserComposite(
                java.util.Arrays.<OutputParser>asList(
                        new WorkspaceOutputParser(),
                        fallback),
                fallback);
        RunAttachProvider attachments = new CatalogRunAttachmentProvider(skillCatalog);
        SkuHitlInterceptor listingHitl = new SkuHitlInterceptor(
                new CreditHoldSupport(creditApplicationService),
                composite,
                artifactPersistPlugin,
                viewResolver,
                generationRunRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                runWorkspaceService,
                attachments);
        return new AgentApplicationService(
                new CreditHoldSupport(creditApplicationService),
                generationRunRepository,
                piSessionSceneRepository,
                sceneRepository,
                sceneCapabilityPackLoader,
                skillCatalog,
                agentSession,
                composite,
                artifactPersistPlugin,
                viewResolver,
                java.util.Collections.<BilledRunInterceptor>singletonList(listingHitl),
                java.util.Collections.<BilledRunListener>singletonList(listingHitl),
                java.util.Collections.<BilledSuspendedHandler>singletonList(listingHitl),
                checkpointer,
                Clock.fixed(NOW, ZoneOffset.UTC),
                runWorkspaceService,
                attachments);
    }

    private static ComputerViewResolver defaultViewResolver() {
        return new ComputerViewResolver(java.util.Arrays.asList(
                new NormalizeViewProjector(),
                new NoSkillMarkdownProjector()));
    }

    @Test
    void prepareGenerationRunReservesAndPersistsRunWithSceneId() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
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
        verify(piSessionSceneRepository).updateSceneIfNeed(eq(ctx.getSessionId()), eq(ECOM_SCENE_ID), eq(ECOM_SCENE_CODE), eq(USER_ID));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareGenerationRunRejectsMissingSceneWithoutReserveOrSave() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊").userId(USER_ID).build()));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals(AgentApplicationService.MSG_SCENE_REQUIRED, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).updateSceneIfNeed(anyString(), anyString(), anyString(), anyString());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void prepareGenerationRunRejectsUnknownSceneId() {
        when(sceneRepository.findByBizId("missing-id")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                        .userId(USER_ID)
                        .sceneId("missing-id")
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_FOUND, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareGenerationRunResolvesSceneByCode() {
        when(sceneRepository.findBySceneCode(ECOM_SCENE_CODE)).thenReturn(Optional.of(ecommerceScene()));
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder()
                .userId(USER_ID)
                .text("选品")
                .skillId("ecommerce-picklist")
                .sceneCode(ECOM_SCENE_CODE)
                .build());

        assertEquals(ECOM_SCENE_CODE, ctx.getSceneCode());
        verify(sceneRepository).findBySceneCode(ECOM_SCENE_CODE);
        verify(sceneRepository, never()).findByBizId(anyString());
    }

    @Test
    void prepareGenerationRunRejectsComingSoonScene() {
        when(sceneRepository.findByBizId(GRAY_SCENE_ID)).thenReturn(Optional.of(grayScene()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                        .userId(USER_ID)
                        .sceneId(GRAY_SCENE_ID)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SCENE_NOT_OPEN, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
    }

    @Test
    void prepareGenerationRunRejectsSessionBoundToOtherScene() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId("fixed-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session", GRAY_SCENE_ID, GRAY_SCENE_CODE)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                        .userId(USER_ID)
                        .sessionId("fixed-session")
                        .sceneId(ECOM_SCENE_ID)
                        .build()));
        assertEquals(AgentApplicationService.MSG_SESSION_SCENE_MISMATCH, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).updateSceneIfNeed(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void prepareGenerationRunWritesSceneWhenSessionHasNoneYet() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId("legacy-session"))
                .thenReturn(Optional.of(new SessionSceneBinding("legacy-session", null, null)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                .userId(USER_ID)
                .sessionId("legacy-session")
                .sceneId(ECOM_SCENE_ID)
                .build());

        verify(piSessionSceneRepository).updateSceneIfNeed("legacy-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID);
    }

    @Test
    void prepareGenerationRunForbiddenWhenSessionOwnedByOtherUser() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId("foreign-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "foreign-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, "other-user")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                        .userId(USER_ID)
                        .sessionId("foreign-session")
                        .sceneId(ECOM_SCENE_ID)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals(SessionQueryService.MSG_UNAVAILABLE, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(piSessionSceneRepository, never()).updateSceneIfNeed(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void prepareGenerationRunAllowsOwnSessionAndStillEnsureBound() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId("own-session"))
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "own-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                .userId(USER_ID)
                .sessionId("own-session")
                .sceneId(ECOM_SCENE_ID)
                .build());

        assertEquals("own-session", ctx.getSessionId());
        verify(piSessionSceneRepository).updateSceneIfNeed("own-session", ECOM_SCENE_ID, ECOM_SCENE_CODE, USER_ID);
        verify(creditApplicationService).reserveOne(USER_ID);
    }

    @Test
    void prepareGenerationRunStoresFixedSessionIdAndNewHoldEachTime() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId("fixed-session-id"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new SessionSceneBinding(
                        "fixed-session-id", ECOM_SCENE_ID, ECOM_SCENE_CODE)));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn("hold-a", "hold-b");
        String sessionId = "fixed-session-id";

        GenerationRunContext first = service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                .userId(USER_ID)
                .sessionId(sessionId)
                .sceneId(ECOM_SCENE_ID)
                .build());
        GenerationRunContext second = service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                .userId(USER_ID)
                .sessionId(sessionId)
                .sceneId(ECOM_SCENE_ID)
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
    void prepareGenerationRunDoesNotCreateRunWhenInsufficient() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.prepareGenerationRun(StartGenerationRunCommand.builder().text("随便聊聊")
                        .userId(USER_ID)
                        .sceneId(ECOM_SCENE_ID)
                        .build()));
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }
    @Test
    void prepareGenerationRunNoSkillRejectsBlankText() {
        stubEcommerceById();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.prepareGenerationRun(
                StartGenerationRunCommand.builder()
                        .userId(USER_ID)
                        .sceneId(ECOM_SCENE_ID)
                        .text("  ")
                        .build()));
        assertEquals(AgentApplicationService.MSG_PROMPT_REQUIRED, ex.getMessage());
        verify(creditApplicationService, never()).reserveOne(anyString());
    }

    @Test
    void prepareGenerationRunBlankSkillIdUsesNoSkillProfile() {
        stubEcommerceById();
        when(piSessionSceneRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(HOLD_ID);

        GenerationRunContext ctx = service.prepareGenerationRun(StartGenerationRunCommand.builder()
                .userId(USER_ID)
                .sceneId(ECOM_SCENE_ID)
                .text("随便聊聊")
                .build());

        assertFalse(ctx.getProfile().isSkillBound());
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
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ns-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-ok", USER_ID, HOLD_ID, "session-ns-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_STARTED, events.get(0).getName());
        SseEvent ready = events.stream()
                .filter(e -> e.getName() == SseEventName.ARTIFACT_READY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertNotNull(ready.getData().get("view"));
        assertTrue(StringUtils.hasText((String) ready.getData().get("artifactRef")));
        assertFalse(ready.getData().containsKey("artifactType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals(Integer.valueOf(2), view.get("version"));
        assertEquals("draft", view.get("title"));
        assertEquals("markdown", view.get("format"));
        assertEquals("这是一段草稿回复", view.get("content"));

        assertEquals(SseEventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_FAILED));
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
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ns-noview")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-noview", USER_ID, HOLD_ID, "session-ns-noview",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        gated.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
    }

    @Test
    void streamGenerationRunNoSkillSettleFailureReleasesAndNeedsReconcile() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-settle", USER_ID, HOLD_ID, "session-ns-settle", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ns-settle", "session-ns-settle", "草稿",
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-ns-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-settle", USER_ID, HOLD_ID, "session-ns-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && "art-1".equals(r.getArtifactRef())
                        && r.getHoldId() == null));
        assertNull(ctx.getHoldId());
    }

    @Test
    void streamGenerationRunNoSkillSettleFailureKeepsHoldWhenReleaseFails() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-ns-settle-rel", USER_ID, HOLD_ID, "session-ns-settle-rel", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ns-settle-rel", "session-ns-settle-rel", "草稿",
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "release boom"))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-ns-settle-rel")).thenReturn(Optional.of(
                GenerationRun.start("run-ns-settle-rel", USER_ID, HOLD_ID, "session-ns-settle-rel",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED_RELEASE_FAILED, failed.getData().get("reason"));
        assertFalse(String.valueOf(failed.getData().get("reason")).contains("已释放"));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && "art-1".equals(r.getArtifactRef())
                        && HOLD_ID.equals(r.getHoldId())));
        assertEquals(HOLD_ID, ctx.getHoldId());
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

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamBilledRunAbortBeforePersistCancelsAndReleases() {
        GenerationRunContext ctx = new GenerationRunContext(
                "run-abort", USER_ID, HOLD_ID, "session-abort", ECOM_SCENE_CODE,
                "你好", SkillRunProfile.noSkill());
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenAnswer(invocation -> {
            subscriber.get().accept(PiEvent.of(PiEventType.AGENT_START, null));
            return TurnResult.ok("run-abort", "session-abort", "草稿",
                    Collections.<com.xmut.forma.pi.ai.message.Message>emptyList());
        });
        when(generationRunRepository.findById("run-abort")).thenReturn(Optional.of(
                GenerationRun.start("run-abort", USER_ID, HOLD_ID, "session-abort",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        service.streamBilledRun(ctx, event -> {
            if (event.getName() == SseEventName.AGENT_STARTED) {
                throw new IllegalStateException("sse broken before persist");
            }
        });

        verify(agentSession).cancel(eq("run-abort"), anyString());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void resumeBilledRunAbortWithLeftoverArtifactRefReleasesOpenExecHold() {
        String runId = "run-abort-hitl";
        String sessionId = "session-abort-hitl";
        String execHoldId = "33333333-3333-3333-3333-333333333333";
        GenerationRun run = GenerationRun.start(runId, USER_ID, null, sessionId,
                ECOM_SCENE_ID, ECOM_SCENE_CODE, "ecommerce-skulist", NOW);
        run.markSettledOnSuspended("art-prior-hitl", NOW);
        when(generationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(creditApplicationService.reserveOne(USER_ID)).thenReturn(execHoldId);
        AtomicReference<Consumer<PiEvent>> subscriber = new AtomicReference<Consumer<PiEvent>>();
        when(agentSession.subscribe(any())).thenAnswer((Answer<AutoCloseable>) invocation -> {
            subscriber.set(invocation.getArgument(0));
            return () -> {
            };
        });
        when(agentSession.resume(any(ResumeRequest.class))).thenAnswer(invocation -> {
            subscriber.get().accept(PiEvent.of(PiEventType.AGENT_START, null));
            return TurnResult.ok(runId, sessionId, "partial",
                    Collections.<com.xmut.forma.pi.ai.message.Message>emptyList());
        });

        service.resumeBilledRun(ResumeGenerationRunCommand.builder()
                .userId(USER_ID)
                .runId(runId)
                .toolCallId(ASK_CALL_ID)
                .optionId(HitlOptions.CONFIRM_EXECUTE)
                .build(), event -> {
            if (event.getName() == SseEventName.AGENT_STARTED) {
                throw new IllegalStateException("sse broken before this-turn persist");
            }
        });

        verify(agentSession).cancel(eq(runId), anyString());
        verify(creditApplicationService).release(USER_ID, execHoldId);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.FAILED));
        assertTrue(captor.getAllValues().stream()
                .noneMatch(r -> r.getStatus() == GenerationRunStatus.SETTLED
                        || r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE));
    }

    @Test
    void cancelRunDelegatesToAgentSession() {
        service.cancelRun("run-1", "sse_timeout");
        verify(agentSession).cancel("run-1", "sse_timeout");
    }

    @Test
    void cancelRunBlankRunIdIsSilent() {
        service.cancelRun("  ", "x");
        service.cancelRun(null, "x");
        verify(agentSession, never()).cancel(anyString(), anyString());
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
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-pl-nosearch")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-nosearch", USER_ID, HOLD_ID, "session-pl-nosearch",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-nosearch"), eq(ECOM_SCENE_CODE),
                eq("picklist"), anyMap(), anyMap());
        verify(runWorkspaceService).deleteRunDirQuietly("session-pl-nosearch", "run-pl-nosearch");
    }

    @Test
    void promptReceivesWorkspaceRoot() throws Exception {
        GenerationRunContext ctx = picklistCtx("run-ws", "session-ws");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ws", "session-ws", VALID_PICKLIST_JSON,
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ws")).thenReturn(Optional.of(
                GenerationRun.start("run-ws", USER_ID, HOLD_ID, "session-ws",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        service.streamBilledRun(ctx, new ArrayList<SseEvent>()::add);

        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        verify(runWorkspaceService).ensureRunDir("session-ws", "run-ws");
    }
    @Test
    void listingPromptReceivesWorkspaceRootAndAttachment() throws Exception {
        GenerationRunContext ctx = listingCtx("run-ws-listing", "session-ws-listing");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ws-listing", "session-ws-listing", VALID_LISTING_JSON,
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ws-listing")).thenReturn(Optional.of(
                GenerationRun.start("run-ws-listing", USER_ID, HOLD_ID, "session-ws-listing",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        service.streamBilledRun(ctx, new ArrayList<SseEvent>()::add);

        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals("ecommerce-skulist", promptCaptor.getValue().getSkillId());
        assertEquals("view.json",
                promptCaptor.getValue().getAttachment().get(TurnDeliverableKeys.OUTPUT));
        verify(runWorkspaceService).ensureRunDir("session-ws-listing", "run-ws-listing");
    }

    @Test
    void outputPointerMissing_releasesWithoutSettle() throws Exception {
        Files.deleteIfExists(tempWorkspace.resolve("view.json"));
        GenerationRunContext ctx = picklistCtx("run-ptr-miss", "session-ptr-miss");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ptr-miss", "session-ptr-miss", "done",
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ptr-miss")).thenReturn(Optional.of(
                GenerationRun.start("run-ptr-miss", USER_ID, HOLD_ID, "session-ptr-miss",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals("output file missing: view.json", failed.getData().get("reason"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
        verify(runWorkspaceService, never()).deleteRunDirQuietly(anyString(), anyString());
    }

    @Test
    void outputPointerFile_settlesAndDeletesWorkspace() throws Exception {
        GenerationRunContext ctx = picklistCtx("run-ptr-ok", "session-ptr-ok");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-ptr-ok", "session-ptr-ok", "done",
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-ptr-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-ptr-ok", USER_ID, HOLD_ID, "session-ptr-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(runWorkspaceService).deleteRunDirQuietly("session-ptr-ok", "run-ptr-ok");
    }

    @Test
    void listingPointerMissing_releasesWithoutSettle() throws Exception {
        GenerationRunContext ctx = listingCtx("run-list-ptr", "session-list-ptr");
        Files.deleteIfExists(tempWorkspace.resolve("view.json"));
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-list-ptr", "session-list-ptr", "done",
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-list-ptr")).thenReturn(Optional.of(
                GenerationRun.start("run-list-ptr", USER_ID, HOLD_ID, "session-list-ptr",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals("output file missing: view.json", failed.getData().get("reason"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(runWorkspaceService, never()).deleteRunDirQuietly(anyString(), anyString());
    }

    @Test
    void streamPicklistRunSettlesWhenSearchSkuSucceeded() {
        GenerationRunContext ctx = picklistCtx("run-pl-searchok", "session-pl-searchok");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-searchok", "session-pl-searchok", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-searchok")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-searchok", USER_ID, HOLD_ID, "session-pl-searchok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-searchok"), eq(ECOM_SCENE_CODE),
                eq("picklist"), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunSettlesOnUsableArtifact() {
        GenerationRunContext ctx = picklistCtx("run-pl-ok", "session-pl-ok");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-ok", "session-pl-ok", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-ok", USER_ID, HOLD_ID, "session-pl-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_STARTED, events.get(0).getName());
        SseEvent ready = events.stream()
                .filter(e -> e.getName() == SseEventName.ARTIFACT_READY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing artifact_ready"));
        assertEquals("art-1", ready.getData().get("artifactRef"));
        assertFalse(ready.getData().containsKey("items"));
        assertNotNull(ready.getData().get("view"));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("picklist", view.get("title"));
        assertEquals(SseEventName.RUN_SETTLED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).release(anyString(), anyString());
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository).update(captor.capture());
        assertEquals(GenerationRunStatus.SETTLED, captor.getValue().getStatus());
        assertEquals("art-1", captor.getValue().getArtifactRef());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(agentSession).prompt(promptCaptor.capture());
        assertEquals("ecommerce-picklist", promptCaptor.getValue().getSkillId());
        assertEquals("帮我选品", promptCaptor.getValue().getText());
        verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-pl-ok"), eq(ECOM_SCENE_CODE),
                eq("picklist"), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunPrefersSkillViewOverLegacyProjection() {
        writeEnvelope("view.json", "artifact.json", SKILL_OWNED_VIEW_JSON);
        GenerationRunContext ctx = picklistCtx("run-pl-view", "session-pl-view");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-view", "session-pl-view", SKILL_OWNED_VIEW_JSON);
        when(generationRunRepository.findById("run-pl-view")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-view", USER_ID, HOLD_ID, "session-pl-view",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent ready = events.stream()
                .filter(e -> e.getName() == SseEventName.ARTIFACT_READY)
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

        List<SseEvent> events = new ArrayList<SseEvent>();
        gated.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void streamPicklistRunSettleFailureReleasesAndNeedsReconcile() {
        GenerationRunContext ctx = picklistCtx("run-pl-settle", "session-pl-settle");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-settle", "session-pl-settle", VALID_PICKLIST_JSON);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-pl-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-settle", USER_ID, HOLD_ID, "session-pl-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && "art-1".equals(r.getArtifactRef())
                        && r.getHoldId() == null));
        assertNull(ctx.getHoldId());
    }

    @Test
    void streamPicklistRunSettleFailureKeepsHoldWhenReleaseFails() {
        GenerationRunContext ctx = picklistCtx("run-pl-settle-rel", "session-pl-settle-rel");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-settle-rel", "session-pl-settle-rel", VALID_PICKLIST_JSON);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "release boom"))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-pl-settle-rel")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-settle-rel", USER_ID, HOLD_ID, "session-pl-settle-rel",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED_RELEASE_FAILED, failed.getData().get("reason"));
        assertFalse(String.valueOf(failed.getData().get("reason")).contains("已释放"));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && HOLD_ID.equals(r.getHoldId())));
        assertEquals(HOLD_ID, ctx.getHoldId());
    }

    @Test
    void streamPicklistRunEmitFailureAfterSettleDoesNotMarkFailed() {
        GenerationRunContext ctx = picklistCtx("run-pl-emit", "session-pl-emit");
        stubEcommercePack();
        stubSubscribeEmittingSearchSkuOk("run-pl-emit", "session-pl-emit", VALID_PICKLIST_JSON);
        when(generationRunRepository.findById("run-pl-emit")).thenReturn(Optional.of(
                GenerationRun.start("run-pl-emit", USER_ID, HOLD_ID, "session-pl-emit",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, event -> {
            if (SseEventName.ARTIFACT_READY.equals(event.getName())) {
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
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_FAILED));
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

        List<SseEvent> events = new ArrayList<SseEvent>();
        gated.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(ComputerViewResolver.MSG_VIEW_UNAVAILABLE, failed.getData().get("reason"));
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

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals("模型超时", failed.getData().get("reason"));
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }
    @Test
    void streamListingRunSettlesWithoutMediaObjectId() {
        GenerationRunContext ctx = listingCtx("run-listing-ok", "session-listing-ok");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-ok", "session-listing-ok", VALID_LISTING_JSON,
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-listing-ok")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-ok", USER_ID, HOLD_ID, "session-listing-ok",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(artifactPersistPlugin).persist(
                eq(USER_ID), eq("run-listing-ok"), eq(ECOM_SCENE_CODE),
                eq("sku"), anyMap(), anyMap());
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
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        when(generationRunRepository.findById("run-listing-bad")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-bad", USER_ID, HOLD_ID, "session-listing-bad",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
    }

    @Test
    void streamListingRunSettleFailureReleasesAndNeedsReconcile() {
        GenerationRunContext ctx = listingCtx("run-listing-settle", "session-listing-settle");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-settle", "session-listing-settle", VALID_LISTING_JSON,
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-listing-settle")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-settle", USER_ID, HOLD_ID, "session-listing-settle",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.RUN_FAILED));
        assertTrue(events.stream().noneMatch(e -> e.getName() == SseEventName.RUN_SETTLED));
        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED, failed.getData().get("reason"));
        verify(creditApplicationService).settle(USER_ID, HOLD_ID);
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && "art-1".equals(r.getArtifactRef())
                        && r.getHoldId() == null));
        assertNull(ctx.getHoldId());
    }

    @Test
    void streamListingRunSettleFailureKeepsHoldWhenReleaseFails() {
        GenerationRunContext ctx = listingCtx("run-listing-settle-rel", "session-listing-settle-rel");
        stubEcommercePack();
        when(agentSession.subscribe(any())).thenReturn(() -> {
        });
        when(agentSession.prompt(any(PromptRequest.class))).thenReturn(
                TurnResult.ok("run-listing-settle-rel", "session-listing-settle-rel", VALID_LISTING_JSON,
                        Collections.<com.xmut.forma.pi.ai.message.Message>emptyList()));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "settle boom"))
                .when(creditApplicationService).settle(USER_ID, HOLD_ID);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDIT_HOLD_INVALID, "release boom"))
                .when(creditApplicationService).release(USER_ID, HOLD_ID);
        when(generationRunRepository.findById("run-listing-settle-rel")).thenReturn(Optional.of(
                GenerationRun.start("run-listing-settle-rel", USER_ID, HOLD_ID, "session-listing-settle-rel",
                        ECOM_SCENE_ID, ECOM_SCENE_CODE, NOW)));

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        SseEvent failed = events.get(events.size() - 1);
        assertEquals(SseEventName.RUN_FAILED, failed.getName());
        assertEquals(AgentApplicationService.SETTLE_FAILED_RELEASE_FAILED, failed.getData().get("reason"));
        assertFalse(String.valueOf(failed.getData().get("reason")).contains("已释放"));
        assertTrue(events.stream().anyMatch(e -> e.getName() == SseEventName.ARTIFACT_READY));
        ArgumentCaptor<GenerationRun> captor = ArgumentCaptor.forClass(GenerationRun.class);
        verify(generationRunRepository, org.mockito.Mockito.atLeastOnce()).update(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == GenerationRunStatus.NEEDS_RECONCILE
                        && HOLD_ID.equals(r.getHoldId())));
        assertEquals(HOLD_ID, ctx.getHoldId());
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

        List<SseEvent> events = new ArrayList<SseEvent>();
        service.streamBilledRun(ctx, events::add);

        assertEquals(SseEventName.RUN_FAILED, events.get(events.size() - 1).getName());
        verify(creditApplicationService).release(USER_ID, HOLD_ID);
        verify(creditApplicationService, never()).settle(anyString(), anyString());
        verify(artifactPersistPlugin, never()).persist(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void prepareResume_withoutCheckpoint_rejects() {
        GenerationRun run = GenerationRun.start("run-no-cp", USER_ID, HOLD_ID,
                "session-no-cp", ECOM_SCENE_ID, ECOM_SCENE_CODE,
                "ecommerce-skulist", NOW);
        when(generationRunRepository.findById("run-no-cp")).thenReturn(Optional.of(run));
        when(checkpointer.loadLatest("run-no-cp")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.prepareResumeRun(ResumeGenerationRunCommand.builder()
                        .userId(USER_ID)
                        .runId("run-no-cp")
                        .toolCallId(ASK_CALL_ID)
                        .optionId(HitlOptions.CONFIRM_EXECUTE)
                        .build()));
        assertEquals(AgentApplicationService.MSG_RESUME_NOT_AWAITING, ex.getMessage());
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
            "{\"view\":{\"version\":1,\"title\":\"Mac Mini 拓展坞 · 上架素材\",\"status\":\"ready\","
                    + "\"blocks\":["
                    + "{\"type\":\"media\",\"role\":\"hero\",\"placeholder\":\"白底主图方案\",\"alt\":\"主图\"},"
                    + "{\"type\":\"section\",\"heading\":\"详情标题\",\"body\":\"Mac Mini 拓展坞\"},"
                    + "{\"type\":\"section\",\"heading\":\"详情正文\",\"body\":\"走线隐藏多口扩展\"},"
                    + "{\"type\":\"section\",\"heading\":\"展示说明\",\"body\":\"主图突出颜色\",\"tone\":\"mute\"}"
                    + "]},"
                    + "\"artifact\":{\"title\":\"Mac Mini 拓展坞 · 上架素材\","
                    + "\"templateId\":\"domestic-generic-default\","
                    + "\"heroPlan\":\"白底俯拍\",\"detailTitle\":\"Mac Mini 拓展坞\","
                    + "\"detailBody\":\"走线隐藏多口扩展\",\"displayNotes\":\"主图突出颜色\","
                    + "\"mediaObjectIds\":[]}}";

    private GenerationRunContext picklistCtx(String runId, String sessionId) {
        return new GenerationRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE,
                "帮我选品", SkillRunProfile.billed("ecommerce-picklist", "picklist"));
    }

    private GenerationRunContext listingCtx(String runId, String sessionId) {
        writeEnvelope("view.json", "artifact.json", VALID_LISTING_JSON);
        return new GenerationRunContext(runId, USER_ID, HOLD_ID, sessionId, ECOM_SCENE_CODE,
                "请为 Mac Mini 拓展坞生成上架素材", SkillRunProfile.billed("ecommerce-skulist", "sku"));
    }
    private void stubSubscribeEmittingSearchSkuOk(String runId, String sessionId, String finalText) {
        stubSubscribeEmittingSearchSkuEnd(runId, sessionId, finalText,
                ToolResult.ok("call-sku", "search_sku", "[{}]"));
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
                    Collections.<com.xmut.forma.pi.ai.message.Message>emptyList());
        });
    }

    private void stubEcommercePack() {
        when(sceneCapabilityPackLoader.load(ECOM_SCENE_CODE)).thenReturn(ecommercePack());
    }

    private static String persistAsForSkill(String id) {
        if ("ecommerce-skulist".equals(id)) {
            return "sku";
        }
        if ("xhs-topiclist".equals(id)) {
            return "xhs_topiclist";
        }
        if ("xhs-note".equals(id)) {
            return "xhs_note";
        }
        if ("xhs-break".equals(id)) {
            return "xhs_break";
        }
        if ("tech-digest".equals(id)) {
            return "tech_digest";
        }
        return "picklist";
    }

    private static SceneCapabilityPack ecommercePack() {
        return new SceneCapabilityPack(ECOM_SCENE_CODE, Arrays.asList(
                skill("ecommerce-picklist",
                        "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md"),
                skill("ecommerce-skulist",
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

    private static Skill catalogSkill(String id) {
        Skill.SkillBuilder builder = Skill.builder()
                .id(id)
                .description(id)
                .promptRef("classpath:scenes/test/" + id + "/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .persistAs(persistAsForSkill(id));
        builder.output("view.json");
        return builder.build();
    }

    private void writeEnvelope(String viewRel, String artifactRel, String envelope) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(envelope);
            writeJson(mapper, viewRel, root.get("view"));
            writeJson(mapper, artifactRel, root.get("artifact"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void writeJson(ObjectMapper mapper, String rel, JsonNode node) throws IOException {
        Path path = tempWorkspace.resolve(rel);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, mapper.writeValueAsBytes(node));
    }
}
