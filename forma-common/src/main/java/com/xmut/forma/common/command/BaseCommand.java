package com.xmut.forma.common.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 写命令基类：携带操作者上下文，供 Application / 下游统一取用。
 * <p>
 * Controllers 组命令时用 {@code .userId(...).username(...)} 注入；
 * 公开接口（注册/登录）可空。勿依赖隐式 ThreadLocal 填充（本产品无 TenantContext）。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class BaseCommand {

    /** 操作者用户 ID（JWT {@code sub}）；公开接口可空 */
    private final String userId;

    /**
     * 操作者用户名；公开接口可空。
     * <p>
     * 特例：公开注册时表示「待注册用户名」（此时 {@link #userId} 为空）。
     */
    private final String username;
}
