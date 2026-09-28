package com.xmut.ebus.domain.business.agent.repository;

import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;

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
    List<PiSessionMeta> selectByUserSince(String userId, Instant since, String sceneCodeOrNull, int limit);

    Optional<PiSessionMeta> findBySessionId(String sessionId);

    /**
     * 回放消息分页：仅 user/assistant 且 content 非空；时间正序。
     *
     * @param nextToken 空=从最新往前取；非空=只取更早（{@code seq < token}）
     * @param limit     本页条数（回放过滤后）
     */
    Page<PiMessageDTO> getMessageList(String sessionId, String nextToken, int limit);
}
