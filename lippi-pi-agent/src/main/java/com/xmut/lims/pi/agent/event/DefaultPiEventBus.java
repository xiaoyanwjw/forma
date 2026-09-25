package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.agent.extension.ContextModifier;
import com.xmut.lims.pi.agent.extension.PromptSegments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
                return clazz.cast(beforeAgentStart(event, handlers));
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
     * Merge handlers：overwrite 每段后写覆盖；append 每段按序拼接。
     */
    private ContextModifier beforeAgentStart(PiEvent event, List<Function<PiEvent, Object>> handlers) {
        if (CollectionUtils.isEmpty(handlers)) {
            return ContextModifier.empty();
        }

        String owStable = null;
        String owContext = null;
        String owVariable = null;
        String apStable = null;
        String apContext = null;
        String apVariable = null;

        for (Function<PiEvent, Object> handler : handlers) {
            Object raw = null;
            try {
                raw = handler.apply(event);
            } catch (Exception e) {
                log.warn("on handler failed for {}: {}", event.getType(), e.toString());
            }
            if (!(raw instanceof ContextModifier)) {
                continue;
            }
            ContextModifier piece = (ContextModifier) raw;
            PromptSegments ow = piece.getOverwrite();
            if (ow != null) {
                owStable = lastNonBlank(owStable, ow.getStable());
                owContext = lastNonBlank(owContext, ow.getContext());
                owVariable = lastNonBlank(owVariable, ow.getVariable());
            }
            PromptSegments ap = piece.getAppend();
            if (ap != null) {
                apStable = join(apStable, ap.getStable());
                apContext = join(apContext, ap.getContext());
                apVariable = join(apVariable, ap.getVariable());
            }
        }

        PromptSegments overwrite = anyText(owStable, owContext, owVariable)
                ? PromptSegments.of(owStable, owContext, owVariable)
                : null;
        PromptSegments append = anyText(apStable, apContext, apVariable)
                ? PromptSegments.of(apStable, apContext, apVariable)
                : null;
        if (overwrite == null && append == null) {
            return ContextModifier.empty();
        }
        return ContextModifier.of(overwrite, append);
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

    private static String lastNonBlank(String previous, String next) {
        if (!StringUtils.hasText(next)) {
            return previous;
        }
        return next.trim();
    }

    private static String join(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        String trimmed = right.trim();
        if (!StringUtils.hasText(left)) {
            return trimmed;
        }
        return left + "\n\n" + trimmed;
    }

    private static boolean anyText(String a, String b, String c) {
        return StringUtils.hasText(a) || StringUtils.hasText(b) || StringUtils.hasText(c);
    }

    @Override
    public void clear() {
        observers.clear();
        ons.clear();
    }
}
