package com.xmut.ebus.domain.business.agent.repository;

import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.domain.business.agent.model.PiLogicalRunRef;
import com.xmut.ebus.domain.business.agent.model.PiMessage;
import com.xmut.ebus.domain.business.agent.model.PiSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code pi_session} 只读查询（显式 userId；与 SessionStore 写端口分离）。
 */
public interface PiSessionQueryRepository {

    /**
     * {@code user_id = userId} 且 {@code updated_at >= since}；可选 scene；新在前；LIMIT。
     * 不含 {@code user_id IS NULL}。
     */
    List<PiSession> selectByUserSince(String userId, Instant since, String sceneCodeOrNull, int limit);

    Optional<PiSession> findBySessionId(String sessionId);

    /**
     * 按逻辑 run 分页（tipSeq=MAX(seq) DESC）；nextToken 空=最新；非空= tipSeq &lt; token。
     * 仅 seq &gt; compact_anchor；忽略空 run_id。
     */
    Page<PiLogicalRunRef> getLogicalRunIds(String sessionId, String nextToken, int limit);

    /**
     * 拉取给定逻辑 runId 的全部可见消息（含 :suspend/:resume），seq 升序。
     * logicalRunIds 空 → 空列表。
     */
    List<PiMessage> getMessagesByLogicalRunIds(String sessionId, List<String> logicalRunIds);
}
