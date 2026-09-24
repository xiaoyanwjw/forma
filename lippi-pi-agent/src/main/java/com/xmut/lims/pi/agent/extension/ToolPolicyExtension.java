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
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolConfig;

import java.util.function.Consumer;

/**
 * [LIMS] Required tool-policy gate: FORBIDDEN/deny → {@code block};
 * unapproved WRITE → {@code needs_hitl}; otherwise allow.
 *
 * <p>Not a GraphNode. Registers {@code BEFORE_TOOL_CALL} / {@code AFTER_TOOL_CALL}
 * on the Session bus. ToolNode evaluates per call (open-source order); HITL
 * suspends that call, not a disguised whole-batch gate.
 */
public final class ToolPolicyExtension implements PiExtension {

    /** Unapproved WRITE: ToolNode requests suspend. */
    public static final String ROUTE_NEEDS_HITL = "needs_hitl";
    /** Gate allows ToolNode to execute. */
    public static final String ROUTE_EXECUTE = "execute";
    /** No remaining calls / already handled. */
    public static final String ROUTE_AGENT = "agent";

    private final ToolConfig config;
    private Consumer<ToolAuditEvent> audit = event -> {
    };

    public ToolPolicyExtension(ToolConfig config) {
        this.config = config;
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
        this.audit = audit != null ? audit : event -> {
        };
    }

    Object onBeforeToolCall(PiEvent event) {
        BeforeToolCallPayload payload = BeforeToolCallPayload.from(event);
        if (payload == null) {
            return BeforeToolCallResult.block("Invalid BEFORE_TOOL_CALL payload");
        }

        return evaluate(payload.getCall(), payload.getToolApproval(), payload.getHumanInput());
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
        if (call == null) {
            return BeforeToolCallResult.block("Invalid tool call entry: null");
        }

        ToolLevel level = config.levelOf(call.getToolName());
        ToolDecision decision = parseDecision(approvalRaw);

        if (level == null || level == ToolLevel.FORBIDDEN) {
            emit(ToolAuditEvent.of(ToolAuditEvent.Kind.FORBIDDEN,
                    call.getToolName(), call.getId(), null,
                    level == null ? "null-level" : "fail-closed"));
            return BeforeToolCallResult.block("Tool forbidden by ToolConfig: " + call.getToolName());
        }

        if (level == ToolLevel.WRITE) {
            if (decision == ToolDecision.DENY) {
                String reason = resolveDenyReason(humanInput);
                emit(ToolAuditEvent.of(ToolAuditEvent.Kind.DENY, call.getToolName(), call.getId(), null, reason));
                return BeforeToolCallResult.block(
                        "Tool write denied" + (reason != null ? ": " + reason : ""));
            }
            if (decision == ToolDecision.APPROVE) {
                emit(ToolAuditEvent.of(ToolAuditEvent.Kind.APPROVE, call.getToolName(), call.getId(), null, null));
                return BeforeToolCallResult.allow();
            }
            emit(ToolAuditEvent.of(ToolAuditEvent.Kind.SUSPEND, call.getToolName(), call.getId(), null, "awaiting approval"));
            return BeforeToolCallResult.needsHitl("awaiting approval");
        }

        return BeforeToolCallResult.allow();
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
