package com.xmut.lims.pi.agent.extension;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.event.BeforeToolCallPayload;
import com.xmut.lims.pi.agent.event.BeforeToolCallResult;
import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolRegistration;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate logic lives on {@code bus.on(BEFORE_TOOL_CALL)}, not GraphNode.
 */
class ToolPolicyExtensionTest {

    @Test
    void null_call_blocks() {
        ToolPolicyExtension ext = new ToolPolicyExtension(DefaultToolConfig.empty());
        BeforeToolCallResult result = ext.evaluate(null, null, null);
        assertThat(result.isBlock()).isTrue();
    }

    @Test
    void forbidden_blocks_and_never_keeps_call() {
        AtomicInteger calls = new AtomicInteger();
        ToolHandler handler = (call, c) -> {
            calls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "no");
        };
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("danger",
                        ToolSchema.builder().name("danger").build(),
                        ToolLevel.FORBIDDEN,
                        handler)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config);

        ToolCallEntry call = new ToolCallEntry("c1", "danger", JsonNodeFactory.instance.objectNode());
        BeforeToolCallResult result = ext.evaluate(call, null, null);

        assertThat(calls.get()).isZero();
        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).containsIgnoringCase("forbidden");
    }

    /** Adam 默认：WRITE 审批关 → 未批准 WRITE 直接 allow（与 READ 同面）。 */
    @Test
    void write_approval_disabled_by_default_allows_write_without_hitl() {
        DefaultToolConfig config = new DefaultToolConfig(java.util.Arrays.asList(
                new ToolRegistration("save",
                        ToolSchema.builder().name("save").build(),
                        ToolLevel.WRITE, null),
                new ToolRegistration("lookup",
                        ToolSchema.builder().name("lookup").build(),
                        ToolLevel.READ, null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config);

        assertThat(ext.isWriteApprovalEnabled()).isFalse();
        assertThat(ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                null, null).isAllow()).isTrue();
        assertThat(ext.evaluate(
                new ToolCallEntry("r1", "lookup", JsonNodeFactory.instance.objectNode()),
                null, null).isAllow()).isTrue();
    }

    @Test
    void unapproved_write_needs_hitl_when_approval_enabled() {
        DefaultToolConfig config = new DefaultToolConfig(java.util.Arrays.asList(
                new ToolRegistration("save",
                        ToolSchema.builder().name("save").build(),
                        ToolLevel.WRITE, null),
                new ToolRegistration("lookup",
                        ToolSchema.builder().name("lookup").build(),
                        ToolLevel.READ, null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        assertThat(ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                null, null).isNeedsHitl()).isTrue();
        assertThat(ext.evaluate(
                new ToolCallEntry("r1", "lookup", JsonNodeFactory.instance.objectNode()),
                null, null).isAllow()).isTrue();
    }

    @Test
    void approve_allows_write_when_approval_enabled() {
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("save",
                        ToolSchema.builder().name("save").build(),
                        ToolLevel.WRITE, null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                ToolDecision.APPROVE, null);
        assertThat(result.isAllow()).isTrue();
    }

    @Test
    void deny_blocks_with_reason_when_approval_enabled() {
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("save",
                        ToolSchema.builder().name("save").build(),
                        ToolLevel.WRITE, null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                ToolDecision.DENY, "nope");
        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).contains("denied").contains("nope");
    }

    @Test
    void register_wires_before_tool_call_on_bus() {
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("save",
                        ToolSchema.builder().name("save").build(),
                        ToolLevel.WRITE, null)));
        PiEventBus bus = new DefaultPiEventBus();
        new ToolPolicyExtension(config, true).register(bus);

        BeforeToolCallResult result = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL, BeforeToolCallPayload.of(
                        new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                        null, null)),
                BeforeToolCallResult.class);
        assertThat(result.isNeedsHitl()).isTrue();
    }
}
