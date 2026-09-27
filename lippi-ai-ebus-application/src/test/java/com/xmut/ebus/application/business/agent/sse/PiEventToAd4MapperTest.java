package com.xmut.ebus.application.business.agent.sse;

import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
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
                "artifact_ready",
                "run_failed",
                "run_settled"
        )), names);
    }

    @Test
    void mapsAgentStartAndEnd() {
        Ad4SseEvent started = PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.AGENT_START)).get();
        assertEquals(Ad4EventName.agent_started, started.getName());
        assertEquals("agent.start", started.getData().get("label"));

        Ad4SseEvent ended = PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.AGENT_END)).get();
        assertEquals(Ad4EventName.agent_ended, ended.getName());
        assertEquals("agent.end", ended.getData().get("label"));
    }

    @Test
    void mapsMessageUpdateToMessageDelta() {
        Optional<Ad4SseEvent> mapped = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.MESSAGE_UPDATE, "chunk"));
        assertTrue(mapped.isPresent());
        assertEquals(Ad4EventName.message_delta, mapped.get().getName());
        assertEquals("chunk", mapped.get().getData().get("text"));
    }

    @Test
    void mapsToolStartAndEnd() {
        assertEquals(Ad4EventName.tool_started,
                PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TOOL_EXECUTION_START)).get().getName());
        assertEquals(Ad4EventName.tool_finished,
                PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TOOL_EXECUTION_END)).get().getName());
    }

    @Test
    void mapsToolCallEntryFieldsForChatSteps() {
        ToolCallEntry call = new ToolCallEntry("call-1", "read_skill", null);
        Ad4SseEvent started = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.TOOL_EXECUTION_START, call)).get();
        assertEquals("read_skill", started.getData().get("toolName"));
        assertEquals("call-1", started.getData().get("toolCallId"));

        ToolResult result = ToolResult.ok("call-1", "read_skill", "ok");
        Ad4SseEvent finished = PiEventToAd4Mapper.mapEvent(
                PiEvent.of(PiEventType.TOOL_EXECUTION_END, result)).get();
        assertEquals("read_skill", finished.getData().get("toolName"));
        assertEquals("call-1", finished.getData().get("toolCallId"));
    }

    @Test
    void doesNotExposeUnmappedPiInternals() {
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.MESSAGE_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.BEFORE_AGENT_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapEvent(PiEvent.of(PiEventType.TURN_START)).isPresent());
    }
}
