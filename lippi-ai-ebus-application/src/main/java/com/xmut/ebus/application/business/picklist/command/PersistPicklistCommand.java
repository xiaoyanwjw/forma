package com.xmut.ebus.application.business.picklist.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 持久化可用选品成果（校验通过后由 Agent 编排调用）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PersistPicklistCommand extends BaseCommand {

    private final String runId;
    private final String sceneCode;
    private final String templateId;
    private final String disclaimer;
    private final String assumptions;
    private final List<PersistPicklistItemCommand> items;
}
