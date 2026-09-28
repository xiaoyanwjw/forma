package com.xmut.ebus.application.identity.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 改密写命令。{@link #getUserId()} 为当前用户。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChangePasswordCommand extends BaseCommand {

    private final String oldPassword;
    private final String newPassword;
}
