package com.xmut.ebus.application.business.agent.sse;

import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
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
    void declaredNamesAreExactlyAd4Seven() {
        Set<String> names = new HashSet<String>();
        for (Ad4EventName name : PiEventToAd4Mapper.declaredEventNames()) {
            names.add(name.wireName());
        }
        assertEquals(new HashSet<String>(Arrays.asList(
                "run_started",
                "message_delta",
                "tool_started",
                "tool_finished",
                "artifact_ready",
                "run_failed",
                "run_settled"
        )), names);
    }

    @Test
    void mapsMessageUpdateToMessageDelta() {
        Optional<Ad4SseEvent> mapped = PiEventToAd4Mapper.mapProgress(
                PiEvent.of(PiEventType.MESSAGE_UPDATE, "chunk"));
        assertTrue(mapped.isPresent());
        assertEquals(Ad4EventName.message_delta, mapped.get().getName());
    }

    @Test
    void mapsToolStartAndEnd() {
        assertEquals(Ad4EventName.tool_started,
                PiEventToAd4Mapper.mapProgress(PiEvent.of(PiEventType.TOOL_EXECUTION_START)).get().getName());
        assertEquals(Ad4EventName.tool_finished,
                PiEventToAd4Mapper.mapProgress(PiEvent.of(PiEventType.TOOL_EXECUTION_END)).get().getName());
    }

    @Test
    void doesNotExposeAgentInternalNames() {
        assertFalse(PiEventToAd4Mapper.mapProgress(PiEvent.of(PiEventType.AGENT_START)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapProgress(PiEvent.of(PiEventType.AGENT_END)).isPresent());
        assertFalse(PiEventToAd4Mapper.mapProgress(PiEvent.of(PiEventType.MESSAGE_START)).isPresent());
    }
}
