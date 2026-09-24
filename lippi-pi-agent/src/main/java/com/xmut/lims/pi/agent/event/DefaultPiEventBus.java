package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.agent.extension.BeforeAgentStartResult;
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
 * In-process {@link PiEventBus}.
 *
 * <p><strong>Emit order (locked, D4):</strong> synchronously invoke every
 * {@code observe} handler (exceptions logged and swallowed), then synchronously
 * await and reduce matching {@code on} handlers.
 *
 * <p>{@code observe} handlers <strong>must be non-blocking</strong>. They are
 * still called on the emit thread so ordering is preserved; callers must not
 * treat observe as a wait barrier and must not perform blocking I/O inside
 * an observer.
 *
 * <p>Only {@link PiEventType#COMMAND}, {@link PiEventType#BEFORE_AGENT_START},
 * {@link PiEventType#BEFORE_TOOL_CALL}, and {@link PiEventType#AFTER_TOOL_CALL}
 * run {@code on} reduce. {@code BEFORE_TOOL_CALL} handler exceptions are
 * fail-closed ({@link BeforeToolCallResult#block(String)}). {@code BEFORE_AGENT_START}
 * merges each handler's three prompt segments (append-only). Other {@code on}
 * exceptions are logged and treated as no return.
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
     * Merge each handler's stable / context / variable increments (append-only),
     * matching {@link BeforeAgentStartResult} semantics. Not last-write-wins.
     */
    private BeforeAgentStartResult beforeAgentStart(PiEvent event, List<Function<PiEvent, Object>> handlers) {
        if (CollectionUtils.isEmpty(handlers)) {
            return BeforeAgentStartResult.empty();
        }

        String stable = null;
        String context = null;
        String variable = null;
        for (Function<PiEvent, Object> handler : handlers) {
            Object raw = null;
            try {
                raw = handler.apply(event);
            } catch (Exception e) {
                log.warn("on handler failed for {}: {}", event.getType(), e.toString());
            }
            if (!(raw instanceof BeforeAgentStartResult)) {
                continue;
            }
            BeforeAgentStartResult piece = (BeforeAgentStartResult) raw;
            stable = join(stable, piece.getStable());
            context = join(context, piece.getContext());
            variable = join(variable, piece.getVariable());
        }
        return BeforeAgentStartResult.of(stable, context, variable);
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

    private static String join(String left, String right) {
        if (right == null || right.trim().isEmpty()) {
            return left;
        }
        String trimmed = right.trim();
        if (left == null || left.trim().isEmpty()) {
            return trimmed;
        }
        return left + "\n\n" + trimmed;
    }

    @Override
    public void clear() {
        observers.clear();
        ons.clear();
    }
}
