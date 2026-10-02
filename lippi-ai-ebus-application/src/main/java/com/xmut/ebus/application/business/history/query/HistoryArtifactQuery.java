package com.xmut.ebus.application.business.history.query;

import com.xmut.ebus.common.query.BaseQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HistoryArtifactQuery extends BaseQuery {

    private final String artifactId;
}
