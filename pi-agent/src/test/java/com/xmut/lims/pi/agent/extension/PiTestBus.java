package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.tool.ToolAuditEvent;
import com.xmut.lims.pi.agent.tool.ToolCatalog;

import java.util.function.Consumer;

/**
 * Test helper: Session-shaped bus with {@link ToolPolicyExtension} already registered.
 */
public final class PiTestBus {

    private PiTestBus() {
    }

    public static PiEventBus withPolicy(ToolCatalog config) {
        return withPolicy(config, null);
    }

    public static PiEventBus withPolicy(ToolCatalog config, Consumer<ToolAuditEvent> audit) {
        PiEventBus bus = new DefaultPiEventBus();
        ToolPolicyExtension policy = new ToolPolicyExtension(config, true);
        if (audit != null) {
            policy.bind(audit);
        }
        policy.register(bus);
        return bus;
    }
}
