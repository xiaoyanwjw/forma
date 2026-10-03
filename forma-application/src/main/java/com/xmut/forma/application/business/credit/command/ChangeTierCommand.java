package com.xmut.forma.application.business.credit.command;

import com.xmut.forma.common.command.BaseCommand;
import com.xmut.forma.domain.business.credit.constant.CreditTier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 手工改档写命令。{@link #userId} 为操作者；{@link #targetUserId} 为被改档用户。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChangeTierCommand extends BaseCommand {

    private final String targetUserId;
    private final CreditTier targetTier;
}
