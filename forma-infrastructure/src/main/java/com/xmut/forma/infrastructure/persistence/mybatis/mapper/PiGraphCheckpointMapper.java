package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.PiGraphCheckpointPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PiGraphCheckpointMapper {

    int upsert(PiGraphCheckpointPO row);

    PiGraphCheckpointPO selectByRunId(@Param("runId") String runId);

    int deleteByRunId(@Param("runId") String runId);
}
