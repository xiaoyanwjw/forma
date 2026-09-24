package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.tool.ToolAuditEvent;
import com.xmut.lims.pi.agent.tool.ToolConfig;

import java.util.function.Consumer;

/**
 * Test helper: Session-shaped bus with {@link ToolPolicyExtension} already registered.
 */
public final class PiTestBus {

    private PiTestBus() {
    }

    public static PiEventBus withPolicy(ToolConfig config) {
        return withPolicy(config, null);
    }

    public static PiEventBus withPolicy(ToolConfig config, Consumer<ToolAuditEvent> audit) {
        PiEventBus bus = new DefaultPiEventBus();
        ToolPolicyExtension policy = new ToolPolicyExtension(config);
        if (audit != null) {
            policy.bind(audit);
        }
        policy.register(bus);
        return bus;
    }
}
