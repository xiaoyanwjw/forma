package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventBus;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.PiExtension;

/**
 * Skill 交付槽位提醒。
 * 功能描述：在 before_agent_start 上按事件里本轮 output 路径 setUser。
 * 关键设计：只钉 output；artifact.json 是 render 中间文件，不进 reminder。
 */
public final class TurnReminderExtension implements PiExtension {

    @Override
    public void register(PiEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus");
        }
        bus.register(PiEventType.BEFORE_AGENT_START, this::onBeforeAgentStart);
    }

    public void onBeforeAgentStart(ContextModifier modifier, PiEvent piEvent) {
        if (modifier == null || piEvent == null) {
            return;
        }
        Object payload = piEvent.getPayload();
        if (!(payload instanceof BeforeAgentStartEvent)) {
            return;
        }
        BeforeAgentStartEvent event = (BeforeAgentStartEvent) payload;
        String output = TurnDeliverableKeys.output(event.getAttachment());
        if (output == null) {
            return;
        }
        modifier.setUser(new DefaultUserModifier(TurnReminder.of(output)));
    }

}
