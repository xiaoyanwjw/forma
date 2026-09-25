package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionPO;
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

    int bumpAfterAppend(@Param("sessionId") String sessionId,
                        @Param("lastRunId") String lastRunId,
                        @Param("messageCount") int messageCount,
                        @Param("updatedAt") Instant updatedAt);

    int bumpMessageCountOnly(@Param("sessionId") String sessionId,
                             @Param("messageCount") int messageCount,
                             @Param("updatedAt") Instant updatedAt);

    int updateTitle(@Param("sessionId") String sessionId,
                    @Param("title") String title,
                    @Param("updatedAt") Instant updatedAt);

    List<PiSessionPO> listRecent(@Param("limit") int limit);

    List<PiSessionPO> listChildrenRoots();

    List<PiSessionPO> listChildrenByParent(@Param("parentSessionId") String parentSessionId);
}
