package com.xmut.forma.pi.agent.event;

import com.xmut.forma.pi.agent.extension.BeforeAgentStartHandler;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 进程内 PiEventBus 实现。
 * 功能描述：同步执行 observe 与可归约 on 处理器。
 * 关键设计：observe 须非阻塞；BEFORE_TOOL_CALL 异常 fail-closed。
 */
public final class DefaultPiEventBus implements PiEventBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPiEventBus.class);

    private final List<Consumer<PiEvent>> observers = new CopyOnWriteArrayList<Consumer<PiEvent>>();
    private final Map<PiEventType, List<Function<PiEvent, Object>>> ons = new ConcurrentHashMap<PiEventType, List<Function<PiEvent, Object>>>();
    private final List<BeforeAgentStartHandler> beforeAgentStartHandlers = new CopyOnWriteArrayList<BeforeAgentStartHandler>();

    @Override
    public AutoCloseable subscribe(Consumer<PiEvent> handler) {
        final Consumer<PiEvent> observer = Objects.requireNonNull(handler, "handler");
        observers.add(observer);
        return new AutoCloseable() {
            @Override
            public void close() {
                observers.remove(observer);
            }
        };
    }

    @Override
    public AutoCloseable register(PiEventType type, Function<PiEvent, Object> handler) {
        final PiEventType eventType = Objects.requireNonNull(type, "type");
        if (eventType == PiEventType.BEFORE_AGENT_START) {
            throw new IllegalArgumentException(
                    "BEFORE_AGENT_START must use register(PiEventType, BeforeAgentStartHandler)");
        }
        final Function<PiEvent, Object> on = Objects.requireNonNull(handler, "handler");

        List<Function<PiEvent, Object>> ons = this.ons.get(eventType);
        if (ons == null) {
            List<Function<PiEvent, Object>> created = new CopyOnWriteArrayList<Function<PiEvent, Object>>();
            List<Function<PiEvent, Object>> existing = this.ons.putIfAbsent(eventType, created);
            ons = existing != null ? existing : created;
        }

        ons.add(on);
        final List<Function<PiEvent, Object>> target = ons;
        return new AutoCloseable() {
            @Override
            public void close() {
                target.remove(on);
            }
        };
    }

    @Override
    public AutoCloseable register(PiEventType type, BeforeAgentStartHandler handler) {
        final PiEventType eventType = Objects.requireNonNull(type, "type");
        if (eventType != PiEventType.BEFORE_AGENT_START) {
            throw new IllegalArgumentException(
                    "BeforeAgentStartHandler can only be registered for BEFORE_AGENT_START");
        }
        final BeforeAgentStartHandler on = Objects.requireNonNull(handler, "handler");
        beforeAgentStartHandlers.add(on);
        return new AutoCloseable() {
            @Override
            public void close() {
                beforeAgentStartHandlers.remove(on);
            }
        };
    }

    @Override
    public void emit(PiEvent event) {
        emit(event, Object.class);
    }

    @Override
    public <T> T emit(PiEvent event, Class<T> clazz) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(clazz, "resultType");

        observe(event);
        return on(event, clazz);
    }

    private void observe(PiEvent event) {
        for (Consumer<PiEvent> handler : observers) {
            try {
                handler.accept(event);
            } catch (RuntimeException ex) {
                log.warn("observe handler failed for {}: {}", event.getType(), ex.toString());
            }
        }
    }

    private <T> T on(PiEvent event, Class<T> clazz) {
        List<Function<PiEvent, Object>> handlers = ons.get(event.getType());
        switch (event.getType()) {
            case BEFORE_AGENT_START:
                return clazz.cast(beforeAgentStart(event));
            case COMMAND:
                return clazz.cast(firstNonNull(event, handlers));
            case BEFORE_TOOL_CALL:
                return clazz.cast(beforeToolCall(event, handlers));
            case AFTER_TOOL_CALL:
                return clazz.cast(afterToolCall(event, handlers));
            default:
                return null;
        }
    }

    /**
     * 新建一份 ContextModifier，按注册顺序交给各 handler 就地累加。
     */
    private ContextModifier beforeAgentStart(PiEvent event) {
        ContextModifier modifier = ContextModifier.empty();
        for (BeforeAgentStartHandler handler : beforeAgentStartHandlers) {
            try {
                handler.apply(modifier, event);
            } catch (Exception e) {
                log.warn("on handler failed for {}: {}", event.getType(), e.toString());
            }
        }
        return modifier;
    }

    private Object firstNonNull(PiEvent event, List<Function<PiEvent, Object>> handlers) {
        if (CollectionUtils.isEmpty(handlers)) {
            return null;
        }

        for (Function<PiEvent, Object> handler : handlers) {
            Object raw = null;
            try {
                raw = handler.apply(event);
            } catch (Exception e) {
                log.warn("on handler failed for {}: {}", event.getType(), e.toString());
            }

            if (raw != null) {
                return raw;
            }
        }
        return null;
    }

    private BeforeToolCallResult beforeToolCall(PiEvent event, List<Function<PiEvent, Object>> handlers) {
        if (CollectionUtils.isEmpty(handlers)) {
            return BeforeToolCallResult.allow();
        }

        BeforeToolCallResult acc = BeforeToolCallResult.allow();
        for (Function<PiEvent, Object> handler : handlers) {
            Object raw;
            try {
                raw = handler.apply(event);
            } catch (RuntimeException ex) {
                log.warn("on handler failed for BEFORE_TOOL_CALL (fail-closed): {}", ex.toString());
                return BeforeToolCallResult.block(ex.getMessage());
            }
            if (!(raw instanceof BeforeToolCallResult)) {
                continue;
            }

            BeforeToolCallResult next = (BeforeToolCallResult) raw;
            if (next.isBlock() || next.isNeedsHitl()) {
                return next;
            }

            acc = next;
        }
        return acc;
    }

    private AfterToolCallResult afterToolCall(PiEvent event, List<Function<PiEvent, Object>> handlers) {
        if (CollectionUtils.isEmpty(handlers)) {
            return null;
        }

        AfterToolCallResult acc = null;
        for (Function<PiEvent, Object> handler : handlers) {
            Object raw;
            try {
                raw = handler.apply(event);
            } catch (RuntimeException ex) {
                log.warn("on handler failed for AFTER_TOOL_CALL: {}", ex.toString());
                continue;
            }
            if (raw instanceof AfterToolCallResult) {
                acc = (AfterToolCallResult) raw;
            }
        }
        return acc;
    }

    @Override
    public void clear() {
        observers.clear();
        ons.clear();
        beforeAgentStartHandlers.clear();
    }
}
