package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionLatestArtifactQuery extends BaseQuery {

    private final String sessionId;
    private final String artifactType;

    public String artifactType() {
        return StringUtils.hasText(artifactType) ? artifactType.trim() : null;
    }
}
