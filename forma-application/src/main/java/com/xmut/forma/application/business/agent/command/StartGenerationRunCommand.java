package com.xmut.forma.application.business.agent.command;

import com.xmut.forma.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 通用 Generation Run：无 Skill markdown 或计费 Skill。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartGenerationRunCommand extends BaseCommand {

    /** 用户意图原文；无 Skill / 计费均必填。 */
    private final String text;

    private final String sessionId;

    private final String sceneId;

    private final String sceneCode;

    /**
     * 目标 Skill。
     * <ul>
     *   <li>blank → 无 Skill markdown 路径（view 门禁后 settle）</li>
     *   <li>已知计费 skill（如 ecommerce-picklist）→ settle 路径</li>
     * </ul>
     */
    private final String skillId;
}
