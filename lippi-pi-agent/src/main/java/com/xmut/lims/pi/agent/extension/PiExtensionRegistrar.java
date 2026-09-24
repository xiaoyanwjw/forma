package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.event.PiEventBus;

/**
 * Startup registration: attach this extension's {@code on}/{@code observe} handlers
 * to the Session-owned bus. No public method-style fan-out.
 */
public interface PiExtensionRegistrar {

    void register(PiEventBus bus);
}
