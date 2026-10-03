package com.xmut.forma.common.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 读查询基类：携带操作者 userId，供 QueryService 统一取用。
 * <p>
 * Controllers 组查询时用 {@code .userId(SecuritySupport.requireUserId())} 注入；
 * Application / QueryService 勿依赖 SecurityContext。禁止 {@code new} + setter。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class BaseQuery {

    /** 操作者用户 ID（JWT {@code sub}） */
    private final String userId;
}
