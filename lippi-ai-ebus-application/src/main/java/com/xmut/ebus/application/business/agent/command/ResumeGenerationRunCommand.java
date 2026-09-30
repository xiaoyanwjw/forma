package com.xmut.ebus.application.business.agent.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Listing / ask_human HITL 续跑：把选项作为 user 消息追加（tool 回执已在 interrupt 时写入）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ResumeGenerationRunCommand extends BaseCommand {

    private final String runId;

    private final String toolCallId;

    /** {@code confirm_execute} | {@code supplement}；可空（仅自由文本时视为补充）。 */
    private final String optionId;

    private final String freeText;

    /** 可选幂等键，透传 {@code ResumeRequest.confirmId}。 */
    private final String confirmId;
}
