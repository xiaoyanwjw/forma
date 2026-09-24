package com.xmut.ebus.application.identity.command;

import com.xmut.ebus.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 注册写命令。
 * <p>
 * 公开注册：{@link #getUsername()} 为待注册用户名，{@link #getUserId()} 为空。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RegisterCommand extends BaseCommand {

    private final String email;
    private final String password;
}
