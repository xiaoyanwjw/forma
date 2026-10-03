package com.xmut.forma.application.identity.command;

import com.xmut.forma.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 改用户名写命令。{@link #getUserId()} 为当前用户；{@link #getUsername()} 为新用户名。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UpdateUsernameCommand extends BaseCommand {
}
