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
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.Tool;
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
    void deniesToolNotInActiveSet() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(java.util.Arrays.asList(
                new Tool("read_skill",
                        ToolSchema.builder().name("read_skill").build(),
                        null),
                new Tool("echo",
                        ToolSchema.builder().name("echo").build(),
                        null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, false);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("c1", "echo", JsonNodeFactory.instance.objectNode()),
                null, null, Collections.singletonList("read_skill"));

        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).containsIgnoringCase("active");
    }

    @Test
    void allowsToolInActiveSet() {
        InMemoryToolCatalog config = InMemoryToolCatalog.of(Collections.singletonList(
                new Tool("read_skill",
                        ToolSchema.builder().name("read_skill").build(),
                        null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, false);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("c1", "read_skill", JsonNodeFactory.instance.objectNode()),
                null, null, Collections.singletonList("read_skill"));

        assertThat(result.isAllow()).isTrue();
    }

    @Test
    void null_call_blocks() {
        ToolPolicyExtension ext = new ToolPolicyExtension(InMemoryToolCatalog.empty());
        BeforeToolCallResult result = ext.evaluate(null, null, null);
        assertThat(result.isBlock()).isTrue();
    }

    @Test
    void unregistered_blocks_and_never_keeps_call() {
        AtomicInteger calls = new AtomicInteger();
        ToolHandler handler = (call, c) -> {
            calls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "no");
        };
        InMemoryToolCatalog config = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("safe",
                        ToolSchema.builder().name("safe").build(),
                        handler)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config);

        ToolCallEntry call = new ToolCallEntry("c1", "danger", JsonNodeFactory.instance.objectNode());
        BeforeToolCallResult result = ext.evaluate(call, null, null);

        assertThat(calls.get()).isZero();
        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).containsIgnoringCase("registered");
    }

    /** Adam 默认：审批关 → 已注册工具直接 allow。 */
    @Test
    void write_approval_disabled_by_default_allows_registered_without_hitl() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(java.util.Arrays.asList(
                new Tool("save",
                        ToolSchema.builder().name("save").build(),
                        null),
                new Tool("lookup",
                        ToolSchema.builder().name("lookup").build(),
                        null)));
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
    void unapproved_tool_needs_hitl_when_approval_enabled() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(java.util.Arrays.asList(
                new Tool("save",
                        ToolSchema.builder().name("save").build(),
                        null),
                new Tool("lookup",
                        ToolSchema.builder().name("lookup").build(),
                        null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        assertThat(ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                null, null).isNeedsHitl()).isTrue();
        assertThat(ext.evaluate(
                new ToolCallEntry("r1", "lookup", JsonNodeFactory.instance.objectNode()),
                null, null).isNeedsHitl()).isTrue();
    }

    @Test
    void approve_allows_when_approval_enabled() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("save",
                        ToolSchema.builder().name("save").build(),
                        null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                ToolDecision.APPROVE, null);
        assertThat(result.isAllow()).isTrue();
    }

    @Test
    void deny_blocks_with_reason_when_approval_enabled() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("save",
                        ToolSchema.builder().name("save").build(),
                        null)));
        ToolPolicyExtension ext = new ToolPolicyExtension(config, true);

        BeforeToolCallResult result = ext.evaluate(
                new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()),
                ToolDecision.DENY, "nope");
        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).contains("denied").contains("nope");
    }

    @Test
    void register_wires_before_tool_call_on_bus() {
        InMemoryToolCatalog config = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("save",
                        ToolSchema.builder().name("save").build(),
                        null)));
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
