package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 通用 Generation Run：dry / 无 Skill / 计费 Skill。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartGenerationRunCommand extends BaseCommand {

    /** 用户意图原文；dry 可空；无 Skill / 计费必填。 */
    private final String text;

    private final String sessionId;

    private final String sceneId;

    private final String sceneCode;

    /**
     * 目标 Skill。
     * <ul>
     *   <li>{@code dryRun=true} + blank → 默认空跑 skill</li>
     *   <li>{@code dryRun=false} + blank → 无 Skill markdown 路径</li>
     *   <li>已知计费 skill（如 ecommerce-picklist）→ settle 路径</li>
     * </ul>
     */
    private final String skillId;

    /** true = 空跑语义（release + run_failed，不 settle）。 */
    private final boolean dryRun;
}
