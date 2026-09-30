package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.event.AfterToolCallResult;
import com.xmut.lims.pi.agent.event.BeforeToolCallPayload;
import com.xmut.lims.pi.agent.event.BeforeToolCallResult;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.tool.ToolAuditEvent;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolCatalog;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 必装工具策略闸门扩展。
 * 功能描述：未注册或未在本轮 active 集中的工具调用一律拒绝。
 * 关键设计：不读 ToolLevel；WRITE 点名审批不在本故事启用（flag 可保留，默认关）。
 */
public final class ToolPolicyExtension implements PiExtension {

    /** AD-S12 选品/Listing 问人工具；独立于 WRITE 审批。 */
    public static final String ASK_HUMAN_TOOL = "ask_human";

    /** Unapproved WRITE: ToolNode requests suspend. */
    public static final String ROUTE_NEEDS_HITL = "needs_hitl";
    /** Gate allows ToolNode to execute. */
    public static final String ROUTE_EXECUTE = "execute";
    /** No remaining calls / already handled. */
    public static final String ROUTE_AGENT = "agent";

    private final ToolCatalog config;
    private final boolean writeApprovalEnabled;
    private Consumer<ToolAuditEvent> audit = event -> {
    };

    /**
     * WRITE 审批默认关（AD-S2）。
     */
    public ToolPolicyExtension(ToolCatalog config) {
        this(config, false);
    }

    /**
     * @param writeApprovalEnabled {@code true} 时已注册且已激活的工具仍走 HITL（不按 level 区分）
     */
    public ToolPolicyExtension(ToolCatalog config, boolean writeApprovalEnabled) {
        this.config = Objects.requireNonNull(config, "config");
        this.writeApprovalEnabled = writeApprovalEnabled;
    }

    public boolean isWriteApprovalEnabled() {
        return writeApprovalEnabled;
    }

    @Override
    public void register(PiEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus");
        }

        bus.register(PiEventType.BEFORE_TOOL_CALL, this::onBeforeToolCall);
        bus.register(PiEventType.AFTER_TOOL_CALL, this::onAfterToolCall);
    }

    public void bind(Consumer<ToolAuditEvent> audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    Object onBeforeToolCall(PiEvent event) {
        BeforeToolCallPayload payload = BeforeToolCallPayload.from(event);
        if (payload == null) {
            return BeforeToolCallResult.block("Invalid BEFORE_TOOL_CALL payload");
        }

        return evaluate(payload.getCall(), payload.getToolApproval(), payload.getHumanInput(),
                payload.getActiveTools());
    }

    Object onAfterToolCall(PiEvent event) {
        return event != null && event.getPayload() instanceof AfterToolCallResult
                ? event.getPayload()
                : null;
    }

    /**
     * Single-call gate used by {@code bus.on(BEFORE_TOOL_CALL)} and unit tests.
     */
    public BeforeToolCallResult evaluate(ToolCallEntry call, Object approvalRaw, Object humanInput) {
        return evaluate(call, approvalRaw, humanInput, null);
    }

    public BeforeToolCallResult evaluate(ToolCallEntry call,
                                         Object approvalRaw,
                                         Object humanInput,
                                         Collection<String> activeTools) {
        if (call == null) {
            return BeforeToolCallResult.block("Invalid tool call entry: null");
        }

        String name = call.getToolName();
        if (!config.isRegistered(name)) {
            emit(ToolAuditEvent.of(ToolAuditEvent.Kind.FORBIDDEN,
                    name, call.getId(), null, "not-registered"));
            return BeforeToolCallResult.block("Tool not registered: " + name);
        }

        if (activeTools != null && !containsName(activeTools, name)) {
            emit(ToolAuditEvent.of(ToolAuditEvent.Kind.FORBIDDEN,
                    name, call.getId(), null, "not-active"));
            return BeforeToolCallResult.block("Tool not in active set: " + name);
        }

        // ask_human：放行进 handler，由 ToolResult.interrupt 挂起（≠ WRITE 审批）。
        if (name != null && ASK_HUMAN_TOOL.equals(name.trim())) {
            return BeforeToolCallResult.allow();
        }

        ToolDecision decision = parseDecision(approvalRaw);
        if (writeApprovalEnabled) {
            if (decision == ToolDecision.DENY) {
                String reason = resolveDenyReason(humanInput);
                emit(ToolAuditEvent.of(ToolAuditEvent.Kind.DENY, name, call.getId(), null, reason));
                return BeforeToolCallResult.block(
                        "Tool write denied" + (reason != null ? ": " + reason : ""));
            }
            if (decision == ToolDecision.APPROVE) {
                emit(ToolAuditEvent.of(ToolAuditEvent.Kind.APPROVE, name, call.getId(), null, null));
                return BeforeToolCallResult.allow();
            }
            emit(ToolAuditEvent.of(ToolAuditEvent.Kind.SUSPEND, name, call.getId(), null, "awaiting approval"));
            return BeforeToolCallResult.needsHitl("awaiting approval");
        }

        return BeforeToolCallResult.allow();
    }

    private static boolean containsName(Collection<String> activeTools, String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        for (String item : activeTools) {
            if (item != null && trimmed.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private void emit(ToolAuditEvent event) {
        audit.accept(event);
    }

    private static ToolDecision parseDecision(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ToolDecision) {
            return (ToolDecision) raw;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        if ("APPROVE".equalsIgnoreCase(s) || "approved".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) {
            return ToolDecision.APPROVE;
        }
        if ("DENY".equalsIgnoreCase(s) || "denied".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            return ToolDecision.DENY;
        }
        return null;
    }

    private static String resolveDenyReason(Object human) {
        if (human instanceof String && !((String) human).trim().isEmpty()) {
            return ((String) human).trim();
        }
        return null;
    }
}
