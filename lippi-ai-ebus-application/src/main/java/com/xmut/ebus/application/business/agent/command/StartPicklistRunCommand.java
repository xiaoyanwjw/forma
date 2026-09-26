package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 启动计费选品生成（预占 + GenerationRun + ecommerce-picklist skill）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartPicklistRunCommand extends BaseCommand {

    /** 用户选品意图原文。 */
    private final String text;

    /** 可复用的 AgentSession ID；空白则新建。 */
    private final String sessionId;

    /** 场景业务 UUID；可与 sceneCode 二选一或同时给出（须一致）。 */
    private final String sceneId;

    /** 稳定场景码（如 ecommerce）；可与 sceneId 二选一或同时给出（须一致）。 */
    private final String sceneCode;
}
