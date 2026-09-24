package com.xmut.ebus.application.identity.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 登录写命令。公开接口：操作者 {@code userId}/{@code username} 为空。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LoginCommand extends BaseCommand {

    private final String account;
    private final String password;
}
