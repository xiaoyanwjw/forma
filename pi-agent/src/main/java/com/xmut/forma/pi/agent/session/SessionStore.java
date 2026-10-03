package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.ai.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * 会话 transcript 持久化端口。
 * 功能描述：提供 Message 投影的读写（getOrCreate / append / load / compact 锚点等）。
 * 关键设计：对外只有 Message 投影，不是 Entry 树协议；与 Checkpointer 键空间/生命周期分离。
 */
public interface SessionStore {

    /**
     * 历史 Redis 会话前缀占位（文档边界用）。
     *
     * <p><b>不做 Redis Session</b>；仅用于单测断言「Session ≠ Checkpoint」前缀隔离。
     *
     * @deprecated 勿据此实现 Redis SessionStore
     */
    @Deprecated
    String REDIS_KEY_PREFIX = "pi:session:";

    /** 兼容旧调用：{@link Session#getMessages()} 视为投影视图。 */
    Session save(Session session);

    /**
     * 兼容旧调用：{@link Session#getMessages()} 视为投影视图。
     */
    Optional<Session> find(String sessionId);

    /**
     * 删除会话及其 transcript；幂等。
     */
    void delete(String sessionId);

    /**
     * 无则建空 transcript；返回稳定会话（含 {@code sessionId}）。
     * {@code meta.sessionId} 可空=新建 UUID；{@code meta.source} 可空=默认 {@code api}。
     */
    Session getOrCreate(Session.Meta meta);

    /**
     * 投影消息：仅 {@code seq > compactAnchorSeq}；不含 {@code system}。
     */
    List<Message> load(String sessionId);

    /**
     * 追加本 turn 增量：分配单调 {@code seq}（从 1）；同 {@code runId} 幂等跳过；过滤 {@code system}。
     */
    void append(String sessionId, String runId, List<Message> messages);

    /**
     * 更新 compact 锚点；可选追加摘要消息。之后 {@link #load} 不见锚点及之前。
     *
     * @param summaryMessage 可空；非 system 时追加一行
     */
    void setCompactAnchor(String sessionId, long seq, Message summaryMessage);

    /** 按 {@code updated_at DESC}；{@code limit} 钳制在 1..200。 */
    List<SessionSummary> listRecent(int limit);

    /**
     * 直接子节点；{@code parentSessionId} 空/null 表示根（{@code parent_session_id IS NULL}）。
     */
    List<SessionSummary> listChildren(String parentSessionId);

    /** 轻量元数据；不 load 全消息。 */
    Optional<SessionSummary> findSummary(String sessionId);

    /** 空白 {@code title} 清空（SQL NULL）。 */
    void updateTitle(String sessionId, String title);
}
