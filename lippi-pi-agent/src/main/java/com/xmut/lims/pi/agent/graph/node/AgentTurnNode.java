package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.agent.agent.DefaultToolLoopGraph;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.agent.CompressionRequest;
import com.xmut.lims.pi.agent.agent.CompressionResult;
import com.xmut.lims.pi.agent.agent.ContextCompressor;
import com.xmut.lims.pi.agent.agent.DefaultPromptBuilder;
import com.xmut.lims.pi.agent.agent.PromptBuilder;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.StubModelProvider;
import com.xmut.lims.pi.ai.model.TokenConsumer;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * agent 轮次薄节点（对齐开源 pi）：
 * <ol>
 *   <li>{@code messages} ← state</li>
 *   <li>{@code system} ← {@link StateKeys#SYSTEM_PROMPT}</li>
 *   <li>compress → format → {@link ModelProvider#stream}（厂商 SSE；Stub 则 fallback {@code complete}）</li>
 *   <li>assistant 写入 messages；流式增量经 {@link #onTextDelta}</li>
 * </ol>
 *
 * <p>生产路径：{@link ModelProvider} / {@link PromptBuilder} 构造期必填（fail-fast）。
 * 图拓扑单测用 {@link #forTopologyTest()}。自定义 agent 行为请直接向
 * {@link DefaultToolLoopGraph#create} 传入 {@link GraphNode}，
 * 勿再包装本类。
 */
public final class AgentTurnNode implements GraphNode {

    private static final Logger log = LoggerFactory.getLogger(AgentTurnNode.class);

    private final ModelProvider model;
    private final PromptBuilder prompt;
    private final ContextCompressor compressor;

    /** 图拓扑单测：显式 Stub，勿用于生产装配。 */
    public static AgentTurnNode forTopologyTest() {
        return new AgentTurnNode(new StubModelProvider(), new DefaultPromptBuilder(), ContextCompressor.NOOP);
    }

    public AgentTurnNode(ModelProvider model) {
        this(model, new DefaultPromptBuilder(), ContextCompressor.NOOP);
    }

    public AgentTurnNode(ModelProvider model, PromptBuilder prompt) {
        this(model, prompt, ContextCompressor.NOOP);
    }

    public AgentTurnNode(ModelProvider model,
                         PromptBuilder prompt,
                         ContextCompressor compressor) {
        this.model = Objects.requireNonNull(model, "modelProvider");
        this.prompt = Objects.requireNonNull(prompt, "promptBuilder");
        this.compressor = compressor != null ? compressor : ContextCompressor.NOOP;
    }

    @Override
    public Map<String, Object> execute(GraphState state, NodeContext ctx) {
        final String sessionId = getState(state, StateKeys.SESSION_ID);
        final String runId = ctx.getRunId();
        final String turnId = UUID.randomUUID().toString();

        emit(ctx, PiEvent.of(PiEventType.TURN_START, turnId, turnId));

        Message system = Message.system(getState(state, StateKeys.SYSTEM_PROMPT));
        List<Message> messages = Message.copyFrom(state.get(StateKeys.MESSAGES));
        messages = compress(messages, system, sessionId, runId);

        final ModelRequest request = ModelRequest.builder()
                .messages(prompt.format(system, messages))
                .tools(toolSchema(state))
                .useCase(resolveUseCase(state))
                .sessionId(sessionId)
                .traceId(ctx.getTraceId())
                .runId(runId)
                .build();

        final AtomicReference<ModelResponse> completed = new AtomicReference<>();
        model.stream(request, new TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                AgentTurnNode.this.onTextDelta(ctx, turnId, delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
                completed.set(response);
            }
        });

        ModelResponse response = ModelResponse.norm(completed.get());
        Map<String, Object> updates = new HashMap<>();
        updates.put(StateKeys.CURRENT_TURN_ID, turnId);

        List<ToolCallEntry> toolCalls = response.getToolCalls();
        updates.put(StateKeys.TOOL_CALLS, toolCalls);

        final String content = response.getContent();
        if (content != null) {
            updates.put(StateKeys.LLM_RESPONSE, content);
        }

        Message assistant = assistant(response);
        if (assistant != null) {
            messages.add(assistant);
        }

        updates.put(StateKeys.MESSAGES, Collections.unmodifiableList(messages));
        emit(ctx, PiEvent.of(PiEventType.TURN_END, turnId, turnId));
        return updates;
    }

    /** [回调] message_update — 助手流式 text delta。 */
    private void onTextDelta(NodeContext ctx, String turnId, String delta) {
        emit(ctx, PiEvent.of(PiEventType.MESSAGE_UPDATE, delta, turnId));
    }

    private static void emit(NodeContext ctx, PiEvent event) {
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(event);
        } catch (RuntimeException ex) {
            log.warn("emitter emit {} failed: {}", event.getType(), ex.toString());
        }
    }

    private static Emitter emitterOf(NodeContext ctx) {
        return ctx != null ? ctx.getEmitter() : null;
    }

    private List<Message> compress(List<Message> messages, Message system,
                                   String sessionId, String runId) {
        List<Message> before = messages;
        try {
            CompressionResult result = compressor.compress(CompressionRequest.builder()
                    .systemMessage(system)
                    .messages(messages)
                    .sessionId(sessionId)
                    .runId(runId)
                    .build());
            if (result.isCompressed()) {
                return new ArrayList<>(result.getMessages());
            }
        } catch (RuntimeException ex) {
            log.warn("ContextCompressor failed; continuing turn uncompressed: {}", ex.toString());
            return before;
        }
        return messages;
    }

    private static String getState(GraphState state, String key) {
        Object raw = state.get(key);
        if (!(raw instanceof String)) {
            return null;
        }
        String text = ((String) raw).trim();
        return text.isEmpty() ? null : text;
    }

    static String resolveUseCase(GraphState state) {
        String fromState = getState(state, StateKeys.MODEL_USE_CASE);
        if (fromState != null) {
            return fromState;
        }
        return InMemoryModelCatalog.DEFAULT_USE_CASE;
    }

    private static Message assistant(ModelResponse response) {
        List<ToolCallEntry> toolCalls = response.getToolCalls();
        if (response.getContent() == null && toolCalls.isEmpty()) {
            return null;
        }
        return Message.assistant(
                response.getContent() != null ? response.getContent() : "",
                toolCalls.isEmpty() ? Collections.emptyList() : toolCalls);
    }

    private static List<ToolSchema> toolSchema(GraphState state) {
        Object raw = state.get(StateKeys.AVAILABLE_TOOLS);
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) {
            return null;
        }
        List<ToolSchema> out = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof ToolSchema) {
                ToolSchema schema = (ToolSchema) item;
                if (schema.getName() != null && !schema.getName().isEmpty()) {
                    out.add(schema);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }
}
