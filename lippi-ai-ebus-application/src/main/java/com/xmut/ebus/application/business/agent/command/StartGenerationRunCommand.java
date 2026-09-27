package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 通用 Generation Run：dry（永不 settle）或计费 Skill。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartGenerationRunCommand extends BaseCommand {

    /** 用户意图原文；dry 可空。 */
    private final String text;

    private final String sessionId;

    private final String sceneId;

    private final String sceneCode;

    /** 目标 Skill；blank 时 dry→默认空跑 skill，计费→ecommerce-picklist。 */
    private final String skillId;

    /** true = 空跑语义（release + run_failed，不 settle）。 */
    private final boolean dryRun;
}
