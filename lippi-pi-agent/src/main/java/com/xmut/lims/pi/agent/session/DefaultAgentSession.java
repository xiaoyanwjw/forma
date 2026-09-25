package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.lims.pi.agent.extension.ContextModifier;
import com.xmut.lims.pi.agent.agent.Agent;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.resource.PiResourceLoader;
import com.xmut.lims.pi.agent.resource.SlashExpansion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * {@link AgentSession} 默认实现：SessionStore hydrate + 委托 {@link Agent}。
 *
 * <p>持有 {@link PiEventBus}：{@link #subscribe} 走 {@code observe}；构造期对扩展
 * {@code register(bus)}；{@link #prompt} / {@link #resume(ResumeRequest)} 经 bus
 * {@code emit}，并把窄口 {@code Emitter} 传入 Loop。
 *
 * <p><b>Transcript 所有权：</b>{@link Session} 负责 merge / delta；
 * 本类负责 I/O 与 fork。产出 {@link TurnInput}（已绑定完整 chat 轴）交给 Loop。
 */
public final class DefaultAgentSession implements AgentSession {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentSession.class);

    private final Agent agent;
    private final SessionStore sessionStore;
    private final PiResourceLoader resourceLoader;
    private final PiEventBus eventBus;

    public DefaultAgentSession(Agent agent, SessionStore sessionStore) {
        this(agent, sessionStore, null, new DefaultPiEventBus());
    }

    public DefaultAgentSession(Agent agent,
                               SessionStore sessionStore,
                               PiResourceLoader resourceLoader,
                               PiEventBus eventBus) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.resourceLoader = resourceLoader;
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    @Override
    public AutoCloseable subscribe(Consumer<PiEvent> handler) {
        return eventBus.subscribe(handler);
    }

    @Override
    public TurnResult prompt(PromptRequest request) {
        if (request == null) {
            return TurnResult.failed(null, "PromptRequest required");
        }

        // 1.获取或创建会话
        final Session session = sessionStore.getOrCreate(Session.Meta
                .builder()
                .sessionId(request.getSessionId())
                .source("api")
                .build());
        String sessionId = session.getSessionId();
        String runId = getRunId(request);

        // 2.尝试命令式处理（可被扩展覆盖）
        TurnResult result = command(request);
        if (result != null) {
            return onAgentEnd(withSession(result, sessionId));
        }

        // 3.展开 slash / skill
        final String context = request.getContext();
        final ExpandedTurn expanded = expand(request);

        // 4.加载历史 + 本轮 user → 合并
        List<Message> existing = sessionStore.load(sessionId);
        List<Message> user = Session.resolveThisTurnUser(request, expanded.text);
        List<Message> messages = Session.merge(existing, user);

        // 5.[回调]触发 before_agent_start
        ContextModifier overwrite;
        try {
            overwrite = beforeAgentStart(runId, expanded.text, context);
        } catch (Exception ex) {
            return onAgentEnd(TurnResult.failed(runId, messageOr(ex, "before_agent_start failed")));
        }

        // 6.构造 TurnInput
        TurnInput input = toTurnInput(request, sessionId, runId, expanded.skillId, context, overwrite, messages);

        // 7.[回调]触发 agent_start
        onAgentStart(sessionId);

        // 8.ConversationLoop.run
        try {
            ConversationResult raw = agent.run(input, eventBus);
            result = mapToTurnResult(raw, sessionId);
        } catch (Exception ex) {
            return onAgentEnd(TurnResult.failed(runId, messageOr(ex, "prompt failed")));
        }

        // 9.持久化增量（前缀不匹配时 fork → sessionId 可能变）
        if (TurnResult.Status.OK.equals(result.getStatus())) {
            try {
                final List<Message> updated = result.getMessages();
                sessionId = persistTurnDelta(sessionId, runId, existing, updated);
                result = withSession(result, sessionId);
            } catch (Exception ex) {
                log.warn("append after OK failed sessionId={} runId={}: {}", sessionId, runId, ex.toString());
            }
        }

        // 10.[回调]触发 agent_end
        return onAgentEnd(result);

    }

    private TurnResult command(PromptRequest request) {
        try {
            return eventBus.emit(PiEvent.of(PiEventType.COMMAND, request), TurnResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ContextModifier beforeAgentStart(String runId, String text, String context) {
        try {
            final PiEvent piEvent = PiEvent.of(PiEventType.BEFORE_AGENT_START, new BeforeAgentStartEvent(runId, text, context));
            ContextModifier result = eventBus.emit(piEvent, ContextModifier.class);

            return result != null ? result : ContextModifier.empty();
        } catch (RuntimeException e) {
            log.warn("before_agent_start failed for runId={} text={} context={}: {}", runId, text, context, e.toString());
            return ContextModifier.empty();
        }
    }

    private void onAgentStart(String sessionId) {
        try {
            eventBus.emit(PiEvent.of(PiEventType.AGENT_START, sessionId));
        } catch (RuntimeException ex) {
            log.warn("eventBus emit failed for {}: {}", sessionId, ex.toString());
        }
    }

    private TurnResult onAgentEnd(TurnResult result) {
        try {
            if (TurnResult.Status.SUSPENDED.equals(result.getStatus())) {
                eventBus.emit(PiEvent.of(PiEventType.SUSPENDED, result));
            }
        } catch (Exception e) {
        }

        try {
            eventBus.emit(PiEvent.of(PiEventType.AGENT_END, result));
        } catch (Exception e) {
        }

        return result;
    }

    @Override
    public TurnResult resume(ResumeRequest request) {
        try {
            onAgentStart(request.getSessionId());

            ConversationResult raw = agent.resume(request, eventBus);

            return onAgentEnd(mapToTurnResult(raw, request.getSessionId()));
        } catch (RuntimeException ex) {
            return onAgentEnd(TurnResult.failed(request.getRunId(), messageOr(ex, "resume failed")));
        }
    }

    @Override
    public CompactResult compact(CompactRequest request) {
        String sessionId = request != null ? request.getSessionId() : null;
        // 诚实桩：Store 锚点 API 已有；CLI /compact 接线后置，不假装已压缩
        return CompactResult.noop(sessionId,
                "compact not wired yet (SessionStore.setCompactAnchor ready; CLI later)");
    }

    @Override
    public void cancel(String runId, String reason) {
        agent.cancel(runId, reason);
    }

    static String getRunId(PromptRequest request) {
        if (request != null && StringUtils.hasText(request.getRunId())) {
            return request.getRunId().trim();
        }
        return UUID.randomUUID().toString();
    }

    private String persistTurnDelta(String sessionId, String runId, List<Message> histBase, List<Message> histDelta) {
        if (!StringUtils.hasText(sessionId)) {
            return sessionId;
        }

        List<Message> delta = Session.computeAppendDelta(histBase, histDelta);
        if (delta != null) {
            sessionStore.append(sessionId, runId, delta);
            return sessionId;
        }

        Session forked = sessionStore.getOrCreate(Session.Meta.builder()
                .source("api")
                .parentSessionId(sessionId)
                .build());
        String forkId = forked.getSessionId();
        log.info("hydrate prefix mismatch → new session {} (was {}) runId={}", forkId, sessionId, runId);

        List<Message> fork = Session.filterNonSystem(histDelta != null ? histDelta : java.util.Collections.emptyList());
        if (!fork.isEmpty()) {
            sessionStore.append(forkId, runId, fork);
        }

        return forkId;
    }

    static TurnInput toTurnInput(PromptRequest request,
                                 String sessionId,
                                 String runId,
                                 String skillId,
                                 String context,
                                 ContextModifier overwrite,
                                 List<Message> messages) {
        return TurnInput.builder()
                .sessionId(sessionId)
                .domain(request.getDomain())
                .runId(runId)
                .skillId(skillId)
                .context(context)
                .contextModifier(overwrite)
                .messages(messages)
                .traceId(request.getTraceId())
                .build();
    }

    private ExpandedTurn expand(PromptRequest request) {
        String text = request.getText();
        String skillId = request.getSkillId();
        if (resourceLoader == null) {
            return new ExpandedTurn(text, skillId);
        }
        // expandSlash：无 text / 非 / 前缀 → unchanged；有模板或 /skill: 才改写
        SlashExpansion expansion = resourceLoader.expandSlash(text);
        if (expansion != null && expansion.isExpanded()) {
            text = expansion.getText();
            if (StringUtils.hasText(expansion.getSkillId())) {
                skillId = expansion.getSkillId();
            }
        }
        return new ExpandedTurn(text, skillId);
    }

    private static TurnResult withSession(TurnResult result, String sessionId) {
        if (result == null) {
            return TurnResult.failed(null, "null command result");
        }
        if (!StringUtils.hasText(sessionId)) {
            return result;
        }
        if (sessionId.equals(result.getSessionId())) {
            return result;
        }
        return result.toBuilder().sessionId(sessionId).build();
    }

    private static String messageOr(Exception ex, String fallback) {
        return ex.getMessage() != null ? ex.getMessage() : fallback;
    }

    static TurnResult mapToTurnResult(ConversationResult raw, String sessionId) {
        if (raw == null) {
            return TurnResult.failed(null, "null ConversationResult");
        }
        return TurnResult.builder()
                .runId(raw.getRunId())
                .sessionId(sessionId)
                .finalResponse(raw.getFinalResponse())
                .messages(raw.getMessages())
                .status(mapStatus(raw.getStatus()))
                .build();
    }

    static TurnResult.Status mapStatus(ConversationResult.Status status) {
        if (status == null) {
            return TurnResult.Status.FAILED;
        }
        switch (status) {
            case OK:
                return TurnResult.Status.OK;
            case FAILED:
                return TurnResult.Status.FAILED;
            case SUSPENDED:
                return TurnResult.Status.SUSPENDED;
            case CANCELLED:
                return TurnResult.Status.CANCELLED;
            case NOT_IMPLEMENTED:
                return TurnResult.Status.NOT_IMPLEMENTED;
            default:
                return TurnResult.Status.FAILED;
        }
    }

    private static final class ExpandedTurn {
        final String text;
        final String skillId;

        ExpandedTurn(String text, String skillId) {
            this.text = text;
            this.skillId = skillId;
        }
    }
}
