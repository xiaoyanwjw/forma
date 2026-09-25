package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 启动空跑（预占 + GenerationRun + AgentSession 桩回合）。
 * <p>
 * {@link #sessionId} 可空：空则新建；非空则复用同一聊天 session（不得复用旧 hold）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StartEmptyRunCommand extends BaseCommand {

    /** 可复用的 AgentSession ID；空白则新建。 */
    private final String sessionId;
}
