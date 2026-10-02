package com.xmut.ebus.application.business.session.support;

import com.xmut.ebus.application.business.session.dto.SessionTurnDTO;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
import com.xmut.ebus.domain.business.agent.model.PiToolCallRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTurnAssemblerTest {

    @Test
    void logicalRunIdStripsHitlSuffixes() {
        assertEquals("abc", SessionTurnAssembler.logicalRunId("abc:suspend"));
        assertEquals("abc", SessionTurnAssembler.logicalRunId("abc:resume"));
        assertEquals("abc", SessionTurnAssembler.logicalRunId("abc"));
        assertNull(SessionTurnAssembler.logicalRunId(" "));
    }

    @Test
    void clustersListingPlanAndExecIntoOneTurn() {
        Instant t1 = Instant.parse("2026-10-02T08:00:00Z");
        Instant t2 = Instant.parse("2026-10-02T08:01:00Z");
        List<PiMessageDTO> rows = Arrays.asList(
                msg("user", "请帮我生成选品清单", 1L, t1, "pick-1", null, null),
                msg("assistant", "{\"output\":\"final.json\"}", 2L, t1, "pick-1", null, null),
                msg("user", "请生成上架素材", 3L, t2, "list-9:suspend", null, null),
                msg("assistant", "plan", 4L, t2, "list-9:suspend", null,
                        Collections.singletonList(new PiToolCallRef("a1", "ask_human"))),
                msg("tool", "wait", 5L, t2, "list-9:suspend", "a1", null),
                msg("assistant", "{\"output\":\"plan/final.json\"}", 6L, t2, "list-9:suspend", null, null),
                msg("user", "{\"optionId\":\"confirm_execute\"}", 7L, t2, "list-9:resume", null, null),
                msg("assistant", "{\"output\":\"exec/final.json\"}", 8L, t2, "list-9:resume", null, null)
        );

        List<SessionTurnDTO> turns = SessionTurnAssembler.assemble(rows);
        assertEquals(2, turns.size());
        assertEquals("pick-1", turns.get(0).getRunId());
        assertEquals("请帮我生成选品清单", turns.get(0).getUserPrompt());
        assertEquals(2, turns.get(0).getMessages().size());

        assertEquals("list-9", turns.get(1).getRunId());
        assertEquals("请生成上架素材", turns.get(1).getUserPrompt());
        assertEquals(6, turns.get(1).getMessages().size());
        assertTrue(turns.get(1).getMessages().stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("optionId")));
        assertFalse(SessionTurnAssembler.isHitlOptionUserContent(turns.get(1).getUserPrompt()));
    }

    private static PiMessageDTO msg(
            String role,
            String content,
            long seq,
            Instant at,
            String runId,
            String toolCallId,
            List<PiToolCallRef> toolCalls) {
        return new PiMessageDTO(role, content, at, seq, toolCallId,
                toolCalls == null ? Collections.<PiToolCallRef>emptyList() : toolCalls, runId);
    }
}
