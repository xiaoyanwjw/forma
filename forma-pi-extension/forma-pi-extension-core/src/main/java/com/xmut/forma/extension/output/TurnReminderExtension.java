package com.xmut.forma.extension.output;

import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventBus;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeModelRequestEvent;
import com.xmut.forma.pi.agent.extension.ModelRequestModifier;
import com.xmut.forma.pi.agent.extension.PiExtension;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;

import java.util.Objects;

/**
 * 普通 Skill 的交付槽位提醒。
 * 功能描述：在 before_model_request 上，为声明了 view/artifact 且没有 plan 槽的 Skill 返回 prefix。
 * 关键设计：不管这一轮返回 null，避免挡住后面的 extension。不读、不改事件里的 messages。
 */
public final class TurnReminderExtension implements PiExtension {

    private final SkillCatalog catalog;

    public TurnReminderExtension(SkillCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public void register(PiEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus");
        }
        bus.register(PiEventType.BEFORE_MODEL_REQUEST, this::onBeforeModelRequest);
    }

    /**
     * skillId 空、目录无此 skill、缺 view/artifact、路径不合法、或已有 planViewPath 时返回 null。
     */
    public ModelRequestModifier onBeforeModelRequest(PiEvent event) {
        BeforeModelRequestEvent payload = payload(event);
        if (payload == null || !StringUtils.hasText(payload.getSkillId())) {
            return null;
        }
        Skill skill = catalog.get(payload.getSkillId()).orElse(null);
        if (skill == null || StringUtils.hasText(skill.getPlanViewPath())) {
            return null;
        }
        String view = TurnReminder.slot(skill.getViewPath());
        String artifact = TurnReminder.slot(skill.getArtifactPath());
        if (view == null || artifact == null) {
            return null;
        }
        return new ModelRequestModifier(TurnReminder.prefix(view, artifact));
    }

    private static BeforeModelRequestEvent payload(PiEvent event) {
        if (event == null || !(event.getPayload() instanceof BeforeModelRequestEvent)) {
            return null;
        }
        return (BeforeModelRequestEvent) event.getPayload();
    }
}
