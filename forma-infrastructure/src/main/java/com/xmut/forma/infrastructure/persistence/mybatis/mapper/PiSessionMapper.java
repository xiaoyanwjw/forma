package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.PiSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface PiSessionMapper {

    int insert(PiSessionPO session);

    PiSessionPO selectById(@Param("sessionId") String sessionId);

    /** 行锁：序列化同会话 append / setCompactAnchor。 */
    PiSessionPO selectByIdForUpdate(@Param("sessionId") String sessionId);

    int deleteById(@Param("sessionId") String sessionId);

    int updateMeta(PiSessionPO session);

    int updateCompactAnchor(@Param("sessionId") String sessionId,
                            @Param("compactAnchorSeq") long compactAnchorSeq,
                            @Param("updatedAt") Instant updatedAt);

    int updateAfterAppend(@Param("sessionId") String sessionId,
                          @Param("lastRunId") String lastRunId,
                          @Param("messageCount") int messageCount,
                          @Param("updatedAt") Instant updatedAt);

    int updateMessageCount(@Param("sessionId") String sessionId,
                           @Param("messageCount") int messageCount,
                           @Param("updatedAt") Instant updatedAt);

    int updateTitle(@Param("sessionId") String sessionId,
                    @Param("title") String title,
                    @Param("updatedAt") Instant updatedAt);

    int updateScene(@Param("sessionId") String sessionId,
                    @Param("sceneId") String sceneId,
                    @Param("sceneCode") String sceneCode,
                    @Param("updatedAt") Instant updatedAt);

    List<PiSessionPO> selectRecent(@Param("limit") int limit);

    /**
     * 本人会话：{@code user_id =} 且 {@code updated_at >= since}；不含 null user_id。
     */
    List<PiSessionPO> selectByUserSince(@Param("userId") String userId,
                                        @Param("since") Instant since,
                                        @Param("sceneCode") String sceneCode,
                                        @Param("limit") int limit);

    /** 仅当当前 {@code user_id} 为空时回填，不覆盖已有归属。 */
    int updateUserIdIfNull(@Param("sessionId") String sessionId,
                           @Param("userId") String userId);

    List<PiSessionPO> selectChildrenRoots();

    List<PiSessionPO> selectChildrenByParent(@Param("parentSessionId") String parentSessionId);
}
