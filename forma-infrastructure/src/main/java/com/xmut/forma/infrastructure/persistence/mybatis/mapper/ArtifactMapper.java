package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.ArtifactPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ArtifactMapper {

    int insert(ArtifactPO artifact);

    int updateByBizId(ArtifactPO artifact);

    ArtifactPO selectByBizId(@Param("bizId") String bizId);

    ArtifactPO selectByRunId(@Param("runId") String runId);

    List<ArtifactPO> selectByUserSince(@Param("userId") String userId,
                                       @Param("since") Instant since,
                                       @Param("artifactTypes") List<String> artifactTypes,
                                       @Param("excludeTypes") List<String> excludeTypes,
                                       @Param("sceneCode") String sceneCode);
}
