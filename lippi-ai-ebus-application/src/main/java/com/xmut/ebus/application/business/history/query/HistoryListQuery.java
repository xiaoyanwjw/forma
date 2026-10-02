package com.xmut.ebus.application.business.history.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HistoryListQuery extends BaseQuery {

    private final String sceneCode;

    public String sceneCode() {
        return StringUtils.hasText(sceneCode) ? sceneCode.trim() : null;
    }
}
