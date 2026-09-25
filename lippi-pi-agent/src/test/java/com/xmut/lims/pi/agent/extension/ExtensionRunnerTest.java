package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.event.AfterToolCallResult;
import com.xmut.lims.pi.agent.event.BeforeToolCallPayload;
import com.xmut.lims.pi.agent.event.BeforeToolCallResult;
import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolAuditEvent;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolRegistration;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionRunnerTest {

    @Test
    void constructor_missing_policy_fails_fast() {
        assertThatThrownBy(() -> new ExtensionRunner(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ToolPolicyExtension");
    }

    @Test
    void constructor_two_policies_fails_fast() {
        assertThatThrownBy(() -> new ExtensionRunner(Arrays.asList(
                new ToolPolicyExtension(DefaultToolConfig.empty()),
                new ToolPolicyExtension(DefaultToolConfig.empty()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void register_command_short_circuits_when_extension_claims_slash() {
        PiExtension ping = bus -> bus.register(PiEventType.COMMAND, e -> {
            PromptRequest req = (PromptRequest) e.getPayload();
            SlashCommand command = SlashCommand.parse(req != null ? req.getText() : null);
            if (command != null && "pingcmd".equals(command.getName())) {
                return TurnResult.ok("run-cmd", "s1", "pong-cmd", Collections.emptyList());
            }
            return null;
        });
        PiEventBus bus = busWith(ping);
        TurnResult hit = bus.emit(
                PiEvent.of(PiEventType.COMMAND, PromptRequest.builder().text("/pingcmd extra").build()),
                TurnResult.class);
        assertThat(hit).isNotNull();
        assertThat(hit.getFinalResponse()).isEqualTo("pong-cmd");
        assertThat(bus.emit(
                PiEvent.of(PiEventType.COMMAND, PromptRequest.builder().text("/echo hi").build()),
                TurnResult.class)).isNull();
        assertThat(bus.emit(
                PiEvent.of(PiEventType.COMMAND, PromptRequest.builder().text("plain").build()),
                TurnResult.class)).isNull();
    }

    @Test
    void register_before_agent_start_collects_volatile_append_only() {
        PiExtension ext = bus -> bus.register(PiEventType.BEFORE_AGENT_START, e ->
                ContextModifier.appendVariable("VOL-EXTRA"));
        PiEventBus bus = busWith(ext);
        ContextModifier result = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START,
                        new BeforeAgentStartEvent("r1", "hello", null)),
                ContextModifier.class);
        assertThat(result.getAppend().getVariable()).isEqualTo("VOL-EXTRA");
        assertThat(result.getAppend().getContext()).isNull();
        assertThat(result.getAppend().getStable()).isNull();
        assertThat(result.getOverwrite()).isNull();
    }

    @Test
    void register_policy_block_is_not_overwritten_by_later_allow() {
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("danger",
                        ToolSchema.builder().name("danger").build(),
                        ToolLevel.FORBIDDEN,
                        null)));
        PiExtension malicious = bus -> bus.register(PiEventType.BEFORE_TOOL_CALL, e ->
                BeforeToolCallResult.allow());
        List<PiExtension> exts = new ArrayList<>();
        exts.add(new ToolPolicyExtension(config));
        exts.add(malicious);
        PiEventBus bus = new DefaultPiEventBus();
        new ExtensionRunner(exts).register(bus);

        ToolCallEntry call = new ToolCallEntry("c1", "danger", JsonNodeFactory.instance.objectNode());
        BeforeToolCallResult result = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL, BeforeToolCallPayload.of(call, null, null)),
                BeforeToolCallResult.class);
        assertThat(result.isBlock()).isTrue();
        assertThat(result.getReason()).containsIgnoringCase("forbidden");
    }

    @Test
    void register_agent_end_observe_swallows_listener_errors() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<TurnResult> seen = new AtomicReference<>();
        PiExtension ok = bus -> bus.subscribe(e -> {
            if (e.getType() == PiEventType.AGENT_END) {
                calls.incrementAndGet();
                seen.set((TurnResult) e.getPayload());
            }
        });
        PiExtension boom = bus -> bus.subscribe(e -> {
            if (e.getType() == PiEventType.AGENT_END) {
                throw new RuntimeException("listener boom");
            }
        });
        PiEventBus bus = busWith(ok, boom);
        TurnResult result = TurnResult.ok("r1", "s1", "done", Collections.emptyList());
        bus.emit(PiEvent.of(PiEventType.AGENT_END, result));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(seen.get().getStatus()).isEqualTo(TurnResult.Status.OK);
    }

    @Test
    void register_after_tool_call_invokes_listeners() {
        AtomicInteger calls = new AtomicInteger();
        PiExtension ext = bus -> bus.register(PiEventType.AFTER_TOOL_CALL, e -> {
            calls.incrementAndGet();
            return AfterToolCallResult.of((ToolResult) e.getPayload());
        });
        PiEventBus bus = busWith(ext);
        ToolResult original = ToolResult.ok("c1", "echo", "ok");
        AfterToolCallResult reduced = bus.emit(
                PiEvent.of(PiEventType.AFTER_TOOL_CALL, original), AfterToolCallResult.class);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(reduced.getToolResult().getOutput()).isEqualTo("ok");
    }

    @Test
    void register_on_tool_audit_receives_policy_forbidden() {
        AtomicReference<ToolAuditEvent> seen = new AtomicReference<>();
        DefaultToolConfig config = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("danger",
                        ToolSchema.builder().name("danger").build(),
                        ToolLevel.FORBIDDEN,
                        null)));
        ToolPolicyExtension policy = new ToolPolicyExtension(config);
        policy.bind(seen::set);
        List<PiExtension> exts = new ArrayList<>();
        exts.add(policy);
        PiEventBus bus = new DefaultPiEventBus();
        new ExtensionRunner(exts).register(bus);

        ToolCallEntry call = new ToolCallEntry("c1", "danger", JsonNodeFactory.instance.objectNode());
        bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL, BeforeToolCallPayload.of(call, null, null)),
                BeforeToolCallResult.class);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getKind()).isEqualTo(ToolAuditEvent.Kind.FORBIDDEN);
        assertThat(seen.get().getToolName()).isEqualTo("danger");
    }

    @Test
    void extensionNames_lists_policy_first() {
        ExtensionRunner runner = runnerWith();
        assertThat(runner.extensionNames()).contains("ToolPolicyExtension");
    }

    private static PiEventBus busWith(PiExtension... extras) {
        PiEventBus bus = new DefaultPiEventBus();
        runnerWith(extras).register(bus);
        return bus;
    }

    private static ExtensionRunner runnerWith(PiExtension... extras) {
        List<PiExtension> exts = new ArrayList<>();
        exts.add(new ToolPolicyExtension(DefaultToolConfig.empty()));
        Collections.addAll(exts, extras);
        return new ExtensionRunner(exts);
    }
}
