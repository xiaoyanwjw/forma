package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionTurnPageQuery extends BaseQuery {

    public static final int TURN_PAGE_DEFAULT = 20;
    public static final int TURN_PAGE_MAX = 50;

    private final String sessionId;
    private final String nextToken;
    private final Integer limit;

    public String nextToken() {
        return StringUtils.hasText(nextToken) ? nextToken.trim() : null;
    }

    public int turnLimit() {
        if (limit == null || limit.intValue() <= 0) {
            return TURN_PAGE_DEFAULT;
        }
        return Math.min(limit.intValue(), TURN_PAGE_MAX);
    }
}
