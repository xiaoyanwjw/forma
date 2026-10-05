package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.GenerationRunPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface GenerationRunMapper {

    int insert(GenerationRunPO run);

    int update(GenerationRunPO run);

    GenerationRunPO selectById(@Param("id") String id);

    String selectLatestUsableArtifactRefBySession(@Param("userId") String userId,
                                                  @Param("sessionId") String sessionId,
                                                  @Param("since") Instant since,
                                                  @Param("artifactType") String artifactType,
                                                  @Param("excludedTypes") List<String> excludedTypes);
}
