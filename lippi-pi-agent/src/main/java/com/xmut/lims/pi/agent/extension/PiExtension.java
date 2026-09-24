package com.xmut.lims.pi.agent.extension;

/**
 * Discoverable plugin (Spring {@code @Component} or SPI). Must self-register
 * via {@link #register(com.xmut.lims.pi.agent.event.PiEventBus)}.
 *
 * <p>No method-style hook API: command / before_agent_start / before_tool_call
 * / after_tool_call / agent_end all go through the bus.
 */
public interface PiExtension extends PiExtensionRegistrar {
}
