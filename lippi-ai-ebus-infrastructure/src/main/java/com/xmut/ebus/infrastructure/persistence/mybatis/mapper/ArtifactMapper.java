package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.ArtifactPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArtifactMapper {

    int insert(ArtifactPO artifact);

    int updateByBizId(ArtifactPO artifact);

    ArtifactPO selectByBizId(@Param("bizId") String bizId);

    ArtifactPO selectByRunId(@Param("runId") String runId);
}
