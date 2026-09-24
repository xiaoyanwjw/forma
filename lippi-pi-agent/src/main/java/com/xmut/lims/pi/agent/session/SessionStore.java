package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.ai.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * Session 持久化端口（≠ {@code Checkpointer} / CheckpointStore）。
 *
 * <p><b>生产真相</b>：SQLite 文件库（{@link SqliteSessionStore}，默认
 * {@code {cwd}/.lippi-pi/state.db}）。Session <b>不做</b> Redis / MySQL（architecture/25）。
 * 单测可用 {@link InMemorySessionStore}（同语义、仅同进程）。
 *
 * <p>键仅为 {@code sessionId}（全局唯一）；无 tenant / user。
 *
 * <p><b>禁止</b>与 Checkpoint 共用 key 空间（Checkpoint={@code pi:checkpoint:}）。
 */
public interface SessionStore {

    /**
     * 历史 Redis 会话前缀占位（文档边界用）。
     *
     * <p><b>生产不做 Redis Session</b>；仅用于单测断言「Session ≠ Checkpoint」前缀隔离。
     * 真持久化 = SQLite（51-17）。
     *
     * @deprecated 见 architecture/25；勿据此实现 Redis SessionStore
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
