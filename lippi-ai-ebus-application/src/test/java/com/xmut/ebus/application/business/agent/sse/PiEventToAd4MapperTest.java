package com.xmut.ebus.application.business.agent.sse;

import com.xmut.ebus.application.business.agent.tool.AskHumanToolHandlerTest;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.event.ToolSuspendPayload;
import com.xmut.lims.pi.agent.session.TurnResult;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiEventToAd4MapperTest {

    @Test
    void declaredNamesAreExactlyAd4ClosedSet() {
        Set<String> names = new HashSet<String>();
        for (Ad4EventName name : PiEventToAd4Mapper.declaredEventNames()) {
            names.add(name.wireName());
        }
        assertEquals(new HashSet<String>(Arrays.asList(
                "run_started",
                "agent_started",
                "message_delta",
                "tool_started",
                "tool_finished",
                "agent_ended",
                "human_input_required",
                "artifact_ready",
                "run_failed",
                "run_settled"
        )), names);
    }

    @Test
    void mapsAgentStartAndEnd() {
        Ad4SseEvent started = PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.AGENT_START)).get();
        assertEquals(Ad4EventName.AGENT_STARTED, started.getName());
        assertEquals("agent.start", started.getData().get("label"));

        Ad4SseEvent ended = PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.AGENT_END)).get();
        assertEquals(Ad4EventName.AGENT_ENDED, ended.getName());
        assertEquals("agent.end", ended.getData().get("label"));
    }

    @Test
    void mapsMessageUpdateToMessageDelta() {
        Optional<Ad4SseEvent> mapped = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.MESSAGE_UPDATE, "chunk"));
        assertTrue(mapped.isPresent());
        assertEquals(Ad4EventName.MESSAGE_DELTA, mapped.get().getName());
        assertEquals("chunk", mapped.get().getData().get("text"));
    }

    @Test
    void mapsToolStartAndEnd() {
        assertEquals(Ad4EventName.TOOL_STARTED,
                PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TOOL_EXECUTION_START)).get().getName());
        assertEquals(Ad4EventName.TOOL_FINISHED,
                PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TOOL_EXECUTION_END)).get().getName());
    }

    @Test
    void mapsToolCallEntryFieldsForChatSteps() {
        ToolCallEntry call = new ToolCallEntry("call-1", "read_skill", null);
        Ad4SseEvent started = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.TOOL_EXECUTION_START, call)).get();
        assertEquals("read_skill", started.getData().get("toolName"));
        assertEquals("call-1", started.getData().get("toolCallId"));

        ToolResult result = ToolResult.ok("call-1", "read_skill", "skill body here");
        Ad4SseEvent finished = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.TOOL_EXECUTION_END, result)).get();
        assertEquals("read_skill", finished.getData().get("toolName"));
        assertEquals("call-1", finished.getData().get("toolCallId"));
        assertEquals(Boolean.TRUE, finished.getData().get("success"));
        assertEquals("skill body here", finished.getData().get("output"));

        ToolResult failed = ToolResult.failed("call-2", "search_sku", "empty hits");
        Ad4SseEvent finishedFail = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.TOOL_EXECUTION_END, failed)).get();
        assertEquals(Boolean.FALSE, finishedFail.getData().get("success"));
        assertEquals("empty hits", finishedFail.getData().get("error"));
    }

    @Test
    void doesNotExposeUnmappedPiInternals() {
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.MESSAGE_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.BEFORE_AGENT_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TURN_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.SUSPENDED, "awaiting approval")).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(
                PiEventType.SUSPENDED, TurnResult.builder().status(TurnResult.Status.SUSPENDED).build())).isPresent());
    }

    @Test
    void mapsAskHumanSuspendToHumanInputRequired() {
        ToolCallEntry call = AskHumanToolHandlerTest.listingAskCall("call-ask");
        Ad4SseEvent ev = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.SUSPENDED, ToolSuspendPayload.of(call, "run-hitl", "ask_human"))).get();
        assertEquals(Ad4EventName.HUMAN_INPUT_REQUIRED, ev.getName());
        assertEquals("策划可以了吗？确认后写出执行稿，或补充需求。", ev.getData().get("question"));
        assertEquals(Boolean.TRUE, ev.getData().get("allowFreeText"));
        assertEquals("call-ask", ev.getData().get("toolCallId"));
        assertEquals("run-hitl", ev.getData().get("runId"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> options = (List<Map<String, String>>) ev.getData().get("options");
        assertEquals("confirm_execute", options.get(0).get("id"));
        assertEquals("确认，出执行稿", options.get(0).get("label"));
        assertEquals("supplement", options.get(1).get("id"));
    }

    @Test
    void mapsAskHumanSuspend_prefersHandlerResultOutputOverRawArgs() throws Exception {
        ToolCallEntry call = AskHumanToolHandlerTest.listingAskCall("call-ask");
        // 乱改 arguments；handler output 才是真源
        ToolResult result = ToolResult.interrupt("call-ask", "ask_human",
                "{\"question\":\"规范化问题\",\"allowFreeText\":false,"
                        + "\"options\":[{\"id\":\"confirm_execute\",\"label\":\"确认，出执行稿\"}]}");
        Ad4SseEvent ev = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.SUSPENDED,
                        ToolSuspendPayload.of(call, "run-2", "ask_human", result))).get();
        assertEquals("规范化问题", ev.getData().get("question"));
        assertEquals(Boolean.FALSE, ev.getData().get("allowFreeText"));
    }

    @Test
    void doesNotMapWriteHitlSuspendAsHumanInput() {
        ToolCallEntry write = new ToolCallEntry("w1", "save", null);
        assertFalse(PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.SUSPENDED, ToolSuspendPayload.of(write, "run-w", "awaiting approval")))
                .isPresent());
    }
}
