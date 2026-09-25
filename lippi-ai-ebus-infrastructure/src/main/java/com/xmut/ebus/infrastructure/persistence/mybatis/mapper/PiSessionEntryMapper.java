package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionEntryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PiSessionEntryMapper {

    int insert(PiSessionEntryPO entry);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    int countBySessionAndRunId(@Param("sessionId") String sessionId,
                               @Param("runId") String runId);

    Long selectNextSeq(@Param("sessionId") String sessionId);

    int countBySessionId(@Param("sessionId") String sessionId);

    List<PiSessionEntryPO> selectProjected(@Param("sessionId") String sessionId,
                                           @Param("anchorSeq") long anchorSeq);

    /** 表内全部 entry（含锚点前）；用于断言「compact 不物理删除」。 */
    List<PiSessionEntryPO> selectAllBySessionId(@Param("sessionId") String sessionId);
}
