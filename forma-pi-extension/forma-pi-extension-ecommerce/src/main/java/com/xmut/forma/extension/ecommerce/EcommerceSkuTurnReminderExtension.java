package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.extension.output.TurnReminder;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventBus;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.PiExtension;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;

import java.util.Objects;

/**
 * 上架素材两段交付槽位提醒。
 * 功能描述：ecommerce-skulist 策划走 plan 路径，确认执行走 exec 路径。
 * 关键设计：skillId 为空时只在 userText 含 confirm_execute 时 setUser；其它直接返回，不 setUser。
 */
public final class EcommerceSkuTurnReminderExtension implements PiExtension {

    static final String SKILL_ID = "ecommerce-skulist";
    private static final String CONFIRM = "confirm_execute";

    private final SkillCatalog catalog;

    public EcommerceSkuTurnReminderExtension(SkillCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public void register(PiEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus");
        }
        bus.register(PiEventType.BEFORE_AGENT_START, this::onBeforeAgentStart);
    }

    /**
     * 仅 ecommerce-skulist，或 skillId 空且 userText 含 confirm_execute。
     * 确认走 view/artifact；否则走 plan。其它不 setUser。
     */
    public void onBeforeAgentStart(ContextModifier modifier, PiEvent event) {
        BeforeAgentStartEvent payload = payload(event);
        if (payload == null) {
            return;
        }
        boolean confirm = payload.getUserText() != null && payload.getUserText().contains(CONFIRM);
        boolean blankSkill = !StringUtils.hasText(payload.getSkillId());
        if (blankSkill) {
            if (confirm) {
                setUser(modifier, true);
            }
            return;
        }
        if (!SKILL_ID.equals(payload.getSkillId().trim())) {
            return;
        }
        setUser(modifier, confirm);
    }

    private void setUser(ContextModifier modifier, boolean exec) {
        Skill skill = catalog.get(SKILL_ID).orElse(null);
        if (skill == null) {
            return;
        }
        String view = TurnReminder.slot(exec ? skill.getViewPath() : skill.getPlanViewPath());
        String artifact = TurnReminder.slot(exec ? skill.getArtifactPath() : skill.getPlanArtifactPath());
        if (view == null || artifact == null) {
            return;
        }
        modifier.setUser(new EcommerceSkuUserModifier(TurnReminder.prefix(view, artifact)));
    }

    private static BeforeAgentStartEvent payload(PiEvent event) {
        if (event == null || !(event.getPayload() instanceof BeforeAgentStartEvent)) {
            return null;
        }
        return (BeforeAgentStartEvent) event.getPayload();
    }
}
