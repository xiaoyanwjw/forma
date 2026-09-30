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
 * AgentSession 默认实现。
 * 功能描述：完成 SessionStore hydrate/merge/落库，并委托内部 Agent 执行。
 * 关键设计：OK / SUSPENDED 落库；resume OK 再落增量；前缀漂移 fork；同逻辑 run 的 suspend/resume
 * 用不同 append 键（{@code runId:suspend} / {@code runId:resume}），避开 SessionStore 同 runId 幂等整批跳过。
 */
public final class DefaultAgentSession implements AgentSession {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentSession.class);

    /** append 幂等键后缀：HITL 挂起阶段。 */
    static final String APPEND_KEY_SUSPEND = ":suspend";
    /** append 幂等键后缀：HITL resume 完成阶段。 */
    static final String APPEND_KEY_RESUME = ":resume";

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

    /**
     * 首轮 / 续聊入口：hydrate SessionStore，委托 {@link Agent#run}，按终态落库后发 AGENT_END。
     *
     * @param request 本轮 prompt（sessionId / runId / text / skillId 等）
     * @return 终态 {@link TurnResult}；失败与命令短路也经 {@link #onAgentEnd}
     */
    @Override
    public TurnResult prompt(PromptRequest request) {
        // 1. 解析 sessionId / runId（空则建会话；缺 runId 发 UUID）
        String sessionId = request.getSessionId();
        String runId = getRunId(request);
        final Session session = sessionStore.getOrCreate(Session.Meta
                .builder()
                .sessionId(sessionId)
                .source("api")
                .build());
        sessionId = session.getSessionId();

        // 2. COMMAND 扩展短路：有人返回 TurnResult 则不再进模型
        TurnResult result = command(request);
        if (result != null) {
            return onAgentEnd(withSession(result, sessionId));
        }

        // 3. 扩 slash、合并 transcript（load 为 compact 投影；本类不改写历史）
        final String context = request.getContext();
        final ExpandedTurn expanded = expand(request);
        List<Message> existing = sessionStore.load(sessionId);
        List<Message> user = Session.resolveThisTurnUser(request, expanded.text);
        List<Message> messages = Session.merge(existing, user);

        // 4. before_agent_start：可改写 system/上下文；抛错则本轮失败
        ContextModifier overwrite;
        try {
            overwrite = beforeAgentStart(runId, expanded.text, context);
        } catch (Exception ex) {
            return onAgentEnd(TurnResult.failed(runId, messageOr(ex, "before_agent_start failed")));
        }

        final TurnInput input = TurnInput.builder()
                .sessionId(sessionId)
                .domain(request.getDomain())
                .runId(runId)
                .traceId(request.getTraceId())
                .context(context)
                .contextModifier(overwrite)
                .skillId(expanded.skillId)
                .messages(messages)
                .build();

        // 5. 调用 Agent.run
        onAgentStart(sessionId);
        try {
            ConversationResult raw = agent.run(input, eventBus);
            result = mapToTurnResult(raw, sessionId);
        } catch (Exception ex) {
            return onAgentEnd(TurnResult.failed(runId, messageOr(ex, "prompt failed")));
        }

        // 6a. OK：原地 append 增量（前缀漂移则 fork）
        if (TurnResult.Status.OK.equals(result.getStatus())) {
            try {
                sessionId = persistTurnDelta(sessionId, runId, existing, result.getMessages());
                result = withSession(result, sessionId);
            } catch (Exception ex) {
                log.warn("append after OK failed sessionId={} runId={}: {}",
                        sessionId, runId, ex.toString());
            }
        }
        // 6b. SUSPENDED：用 runId:suspend 落库（避开同 runId 幂等跳过）
        if (TurnResult.Status.SUSPENDED.equals(result.getStatus())) {
            try {
                sessionId = persistTurnDelta(sessionId, toRunId(runId, APPEND_KEY_SUSPEND), existing, result.getMessages());
                result = withSession(result, sessionId);
            } catch (Exception ex) {
                log.warn("append after SUSPENDED failed sessionId={} runId={}: {}",
                        sessionId, runId, ex.toString());
            }
        }

        // 7. 统一终态出口（SUSPENDED → AGENT_END）
        return onAgentEnd(result);
    }

    /** COMMAND 扩展：有人返回 TurnResult 则吞掉本轮；异常/无人处理 → null 继续主路径。 */
    private TurnResult command(PromptRequest request) {
        try {
            return eventBus.emit(PiEvent.of(PiEventType.COMMAND, request), TurnResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 跑模型前的上下文改写钩子；emit 失败降级为空 modifier，不阻断主路径。 */
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

    /** 通知订阅方「模型回合开始」；emit 失败只打日志。 */
    private void onAgentStart(String sessionId) {
        try {
            eventBus.emit(PiEvent.of(PiEventType.AGENT_START, sessionId));
        } catch (RuntimeException ex) {
            log.warn("eventBus emit failed for {}: {}", sessionId, ex.toString());
        }
    }

    /**
     * 统一终态出口：HITL 先发 {@code SUSPENDED}，再发 {@code AGENT_END}。
     * emit 失败吞掉，避免总线问题掩盖业务结果。
     */
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

    /**
     * HITL 续跑入口：委托 {@link Agent#resume}，OK 时用 {@code runId:resume} 落增量。
     *
     * @param request 续跑请求（runId / sessionId / toolCallId 或 WRITE decision）
     * @return 终态 {@link TurnResult}
     */
    @Override
    public TurnResult resume(ResumeRequest request) {
        // 1. 解析 sessionId / runId
        String sessionId = request.getSessionId();
        String runId = StringUtils.hasText(request.getRunId())
                ? request.getRunId().trim()
                : UUID.randomUUID().toString();
        try {
            // 2. hydrate SessionStore（作 append 前缀基线）
            if (StringUtils.hasText(sessionId)) {
                sessionStore.getOrCreate(Session.Meta.builder()
                        .sessionId(sessionId)
                        .source("api")
                        .build());
            }
            List<Message> existing = StringUtils.hasText(sessionId)
                    ? sessionStore.load(sessionId)
                    : Collections.<Message>emptyList();

            // 3. 调用 Agent.resume
            onAgentStart(sessionId);
            ConversationResult raw = agent.resume(request, eventBus);
            TurnResult result = mapToTurnResult(raw, sessionId);

            // 4. OK：用 runId:resume 落 tool-result 及后续增量（前缀漂移则 fork）
            if (TurnResult.Status.OK.equals(result.getStatus())
                    && StringUtils.hasText(sessionId)) {
                try {
                    sessionId = persistTurnDelta(
                            sessionId,
                            toRunId(runId, APPEND_KEY_RESUME),
                            existing,
                            result.getMessages());
                    result = withSession(result, sessionId);
                } catch (Exception ex) {
                    log.warn("append after resume OK failed sessionId={} runId={}: {}",
                            sessionId, runId, ex.toString());
                }
            }

            // 5. 统一终态出口
            return onAgentEnd(result);
        } catch (RuntimeException ex) {
            return onAgentEnd(TurnResult.failed(runId, messageOr(ex, "resume failed")));
        }
    }

    static String toRunId(String runId, String suffix) {
        if (!StringUtils.hasText(runId)) {
            return suffix;
        }
        return runId.trim() + suffix;
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

    /**
     * 把本轮产出写回 SessionStore：能接前缀则 append，否则 fork 子会话。
     *
     * @param sessionId 当前会话
     * @param runId     append 幂等键（可为 {@code runId:suspend} / {@code runId:resume}）
     * @param histBase  进 Agent 前的 Store 投影
     * @param histDelta Agent 返回的完整 messages
     * @return 实际使用的 sessionId（fork 时为新 id）
     */
    private String persistTurnDelta(String sessionId, String runId, List<Message> histBase, List<Message> histDelta) {
        if (!StringUtils.hasText(sessionId)) {
            return sessionId;
        }

        // 1. 前缀匹配：原地 append 后缀差集
        List<Message> delta = Session.computeAppendDelta(histBase, histDelta);
        if (delta != null) {
            sessionStore.append(sessionId, runId, delta);
            return sessionId;
        }

        // 2. 前缀漂移：fork 子会话写全量非 system，父会话不动
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

    private ExpandedTurn expand(PromptRequest request) {
        String text = request.getText();
        String skillId = request.getSkillId();
        if (resourceLoader == null) {
            return new ExpandedTurn(text, skillId);
        }
        // 无 text / 非 / 前缀 → 原样；命中模板或 /skill: 才改写 text/skillId
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
