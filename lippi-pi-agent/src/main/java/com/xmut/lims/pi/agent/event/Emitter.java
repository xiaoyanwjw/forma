package com.xmut.lims.pi.agent.event;

/**
 * Narrow emit port for Loop/Node. Implementations must not expose subscribe APIs.
 */
public interface Emitter {

    /**
     * Emit an event to all observers and handlers.
     */
    void emit(PiEvent event);

    /**
     * Emit an event to all observers and handlers, returning a reduced result of the specified type.
     */
    <T> T emit(PiEvent event, Class<T> resultType);
}
