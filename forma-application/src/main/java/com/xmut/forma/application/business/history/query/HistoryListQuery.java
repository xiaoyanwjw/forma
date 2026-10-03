package com.xmut.forma.application.business.history.query;

import com.xmut.forma.common.query.BaseQuery;
import com.xmut.forma.common.util.StringUtils;
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
