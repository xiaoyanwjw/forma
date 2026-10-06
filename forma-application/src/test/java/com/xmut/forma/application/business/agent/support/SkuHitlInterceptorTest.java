package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.agent.dto.GenerationRunContext;
import com.xmut.forma.application.business.agent.sse.SseEvent;
import com.xmut.forma.application.business.computer.ComputerViewResolver;
import com.xmut.forma.application.business.computer.NormalizeViewProjector;
import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.application.business.agent.workspace.RunWorkspaceService;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.output.*;
import com.xmut.forma.common.output.RunAttachProvider;
import com.xmut.forma.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkuHitlInterceptorTest {

    @Test
    void plan_settle_parses_with_null_resume_and_wraps_missing_file() {
        final OutputParseContext[] seen = new OutputParseContext[1];
        OutputParser parser = new OutputParser() {
            @Override
            public boolean supports(OutputParseContext ctx) {
                seen[0] = ctx;
                return true;
            }

            @Override
            public ParsedGenerationOutput parse(OutputParseContext ctx) {
                seen[0] = ctx;
                throw new IllegalArgumentException("output file missing: plan/view.json");
            }
        };
        SkuHitlInterceptor interceptor = interceptor(parser);
        BilledRunContext billed = listingContext();
        billed.setResumeOptionId("confirm_execute");
        billed.appendAssistantDelta("{\"output\":\"plan/final.json\"}");
        billed.bindTurnResult(com.xmut.forma.pi.agent.session.TurnResult.builder()
                .status(com.xmut.forma.pi.agent.session.TurnResult.Status.SUSPENDED)
                .finalResponse("{\"output\":\"plan/final.json\"}")
                .messages(Collections.singletonList(Message.assistant("{\"output\":\"plan/final.json\"}", null)))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.onSuspended(billed, new ArrayList<SseEvent>()::add));

        assertEquals("output file missing: plan/view.json", ex.getMessage());
        assertNull(seen[0].getResumeOptionId());
        assertEquals(TurnAttachment.empty(), seen[0].getAttachment());
    }

    @Test
    void plan_settle_uses_parser_payload_not_assistant_pointer() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", Integer.valueOf(1));
        view.put("title", "from-disk");
        view.put("status", "ready");
        view.put("blocks", Collections.emptyList());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("templateId", "domestic-generic-default");
        payload.put("driver", "痛点驱动");
        payload.put("titleDraft", "from-disk");
        payload.put("frames", Arrays.asList("一", "二", "三"));
        payload.put("modules", Arrays.asList("甲", "乙", "丙"));
        OutputParser parser = new OutputParser() {
            @Override
            public boolean supports(OutputParseContext ctx) {
                return ctx != null && ctx.getResumeOptionId() == null;
            }

            @Override
            public ParsedGenerationOutput parse(OutputParseContext ctx) {
                return new ParsedGenerationOutput(view, payload);
            }
        };
        ArtifactPersistPlugin persist = mock(ArtifactPersistPlugin.class);
        when(persist.persist(anyString(), anyString(), anyString(), anyString(), anyMap(), anyMap()))
                .thenReturn(new PersistedGenerationArtifact("art-1", Collections.<String, Object>emptyMap()));
        SkuHitlInterceptor interceptor = interceptor(parser, persist);
        BilledRunContext billed = listingContext();
        billed.appendAssistantDelta("{\"output\":\"nope.json\"}");
        List<SseEvent> events = new ArrayList<SseEvent>();

        interceptor.onSuspended(billed, events::add);

        verify(persist).persist(eq("user-1"), eq("run-1"), eq("ecommerce"),
                eq("listing_plan"), anyMap(), eq(payload));
    }

    private static SkuHitlInterceptor interceptor(OutputParser parser) {
        return interceptor(parser, mock(ArtifactPersistPlugin.class));
    }

    private static SkuHitlInterceptor interceptor(OutputParser parser, ArtifactPersistPlugin persist) {
        RunWorkspaceService workspace = mock(RunWorkspaceService.class);
        when(workspace.runDir(anyString(), anyString())).thenReturn(Paths.get("/tmp/forma-run"));
        RunAttachProvider attachments = mock(RunAttachProvider.class);
        when(attachments.of(anyString(), nullable(String.class))).thenReturn(TurnAttachment.empty());
        return new SkuHitlInterceptor(
                new CreditHoldSupport(mock(CreditApplicationService.class)),
                parser,
                persist,
                new ComputerViewResolver(Collections.singletonList(new NormalizeViewProjector())),
                mock(GenerationRunRepository.class),
                Clock.systemUTC(),
                workspace,
                attachments);
    }

    private static BilledRunContext listingContext() {
        GenerationRunContext run = new GenerationRunContext(
                "run-1", "user-1", "hold-1", "session-1", "ecommerce",
                "上架", SkillRunProfile.billed("ecommerce-skulist", "sku"));
        return new BilledRunContext(run);
    }
}
