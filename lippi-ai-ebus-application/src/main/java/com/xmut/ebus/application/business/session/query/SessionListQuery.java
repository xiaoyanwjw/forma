package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionListQuery extends BaseQuery {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    /** 可选场景过滤；空白经 {@link #sceneCode()} 归一为 null */
    private final String sceneCode;
    /** 可选页大小；经 {@link #limit()} clamp */
    private final Integer limit;

    public String sceneCode() {
        return StringUtils.hasText(sceneCode) ? sceneCode.trim() : null;
    }

    public int limit() {
        if (limit == null || limit.intValue() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit.intValue(), MAX_LIMIT);
    }
}
