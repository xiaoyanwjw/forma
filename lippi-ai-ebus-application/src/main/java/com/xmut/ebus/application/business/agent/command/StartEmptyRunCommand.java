package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 启动空跑（预占 + GenerationRun + AgentSession 桩回合）。
 * <p>
 * {@link #sessionId} 可空：空则新建；非空则复用同一聊天 session（不得复用旧 hold）。
 * {@link #sceneId} / {@link #sceneCode} 至少一项非空（AD-15）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartEmptyRunCommand extends BaseCommand {

    /** 可复用的 AgentSession ID；空白则新建。 */
    private final String sessionId;

    /** 场景业务 UUID；可与 sceneCode 二选一或同时给出（须一致）。 */
    private final String sceneId;

    /** 稳定场景码（如 ecommerce）；可与 sceneId 二选一或同时给出（须一致）。 */
    private final String sceneCode;
}
