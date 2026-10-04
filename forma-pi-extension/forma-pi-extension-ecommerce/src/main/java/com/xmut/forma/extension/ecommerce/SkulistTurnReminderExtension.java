package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.extension.output.TurnReminder;
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
 * 上架素材两段交付槽位提醒。
 * 功能描述：ecommerce-skulist 策划走 plan 路径，确认执行走 exec 路径。
 * 关键设计：skillId 为空时只在 thisTurnText 含 confirm_execute 时给出执行槽；其它返回 null。
 * 不读、不改事件里的 messages。
 */
public final class SkulistTurnReminderExtension implements PiExtension {

    static final String SKILL_ID = "ecommerce-skulist";
    private static final String CONFIRM = "confirm_execute";

    private final SkillCatalog catalog;

    public SkulistTurnReminderExtension(SkillCatalog catalog) {
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
     * 仅 ecommerce-skulist，或 skillId 空且 thisTurnText 含 confirm_execute。
     * 确认走 view/artifact；否则走 plan。其它返回 null。
     */
    public ModelRequestModifier onBeforeModelRequest(PiEvent event) {
        BeforeModelRequestEvent payload = payload(event);
        if (payload == null) {
            return null;
        }
        boolean confirm = payload.getThisTurnText() != null && payload.getThisTurnText().contains(CONFIRM);
        boolean blankSkill = !StringUtils.hasText(payload.getSkillId());
        if (blankSkill) {
            return confirm ? modifier(true) : null;
        }
        if (!SKILL_ID.equals(payload.getSkillId().trim())) {
            return null;
        }
        return modifier(confirm);
    }

    private ModelRequestModifier modifier(boolean exec) {
        Skill skill = catalog.get(SKILL_ID).orElse(null);
        if (skill == null) {
            return null;
        }
        String view = TurnReminder.slot(exec ? skill.getViewPath() : skill.getPlanViewPath());
        String artifact = TurnReminder.slot(exec ? skill.getArtifactPath() : skill.getPlanArtifactPath());
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
