package com.xmut.lims.pi.agent;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationResultTest {

    @Test
    void factories_are_null_safe() {
        assertThat(ConversationResult.failed(null).getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(ConversationResult.failed(null).getMessages()).isEmpty();

        assertThat(ConversationResult.suspended(null).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(ConversationResult.cancelled("stop").getStatus()).isEqualTo(ConversationResult.Status.CANCELLED);
        assertThat(ConversationResult.cancelled("stop").getFinalResponse()).isEqualTo("stop");

        assertThat(ConversationResult.ok("hi", null).getMessages()).isEmpty();
        assertThat(ConversationResult.ok("hi", Collections.emptyList()).getStatus())
                .isEqualTo(ConversationResult.Status.OK);

        assertThat(ConversationResult.ok("r1", "hi", Collections.emptyList()).getRunId()).isEqualTo("r1");
        assertThat(ConversationResult.failed("r2", "boom").getRunId()).isEqualTo("r2");
        assertThat(ConversationResult.cancelled("r3", "stop").getRunId()).isEqualTo("r3");
    }
}
