package com.xmut.forma.domain.business.artifact;

import java.util.List;

/**
 * 会话历史 / 侧栏最近成果要排除的 artifact_type。
 * 平台固定 chat；场景中间稿由 Skill {@code hideFromHistory} 补充。
 */
public interface ArtifactHistoryExcludeCodes {

    List<String> codes();
}
