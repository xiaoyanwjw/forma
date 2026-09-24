package com.xmut.lims.pi.agent.event;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Unified lifecycle bus: {@code observe} is read-only fan-out;
 * {@code on} is typed, awaited, and reduced.
 *
 * <p>{@code emit} order is locked: all {@code observe} handlers run synchronously
 * (exceptions swallowed), then matching {@code on} handlers reduce.
 */
public interface PiEventBus extends Emitter {

    /**
     * Subscribe a read-only observer. Invoked synchronously during {@link #emit}
     * before {@code on} reduce so ordering is preserved.
     *
     * <p>Handlers <strong>must be non-blocking</strong>: no I/O, no waiting, no
     * barriers. Exceptions are logged and swallowed; they must not stop emit
     * or later observers.
     *
     * @return closeable that unsubscribes this handler
     */
    AutoCloseable subscribe(Consumer<PiEvent> handler);

    /**
     * Register a mutable handler for {@code type}. Invoked synchronously during
     * {@code emit} after observe fan-out; return values are reduced by type.
     *
     * @return closeable that unsubscribes this handler
     */
    AutoCloseable register(PiEventType type, Function<PiEvent, Object> handler);

    /**
     * Drop all observe and on handlers.
     */
    void clear();
}
