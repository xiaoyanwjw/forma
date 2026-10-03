package com.xmut.forma.application.identity.command;

import com.xmut.forma.common.command.BaseCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 注册写命令。
 * <p>
 * 公开注册：{@link #getUsername()} 为待注册用户名，{@link #getUserId()} 为空。
 * {@code agreedToAiDisclaimer} 仅作门禁，不落库。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RegisterCommand extends BaseCommand {

    private final String email;
    private final String password;
    private final boolean agreedToAiDisclaimer;
}
