package com.xmut.forma.pi.ai.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    @Test
    void interrupt_isSuccessWithFlag() {
        ToolResult r = ToolResult.interrupt("c1", "ask_human", "{\"question\":\"q\"}");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isInterrupt()).isTrue();
        assertThat(r.getOutput()).contains("question");
        assertThat(r.getErrorMessage()).isNull();
    }

    @Test
    void ok_isNotInterrupt() {
        assertThat(ToolResult.ok("c1", "echo", "out").isInterrupt()).isFalse();
    }

    @Test
    void failed_isNotInterrupt() {
        assertThat(ToolResult.failed("c1", "echo", "err").isInterrupt()).isFalse();
    }
}
