package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.GenerationRunPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationRunMapper {

    int insert(GenerationRunPO run);

    int update(GenerationRunPO run);

    GenerationRunPO selectById(@Param("id") String id);

    String selectLatestUsableArtifactRefBySession(@Param("userId") String userId,
                                                  @Param("sessionId") String sessionId);
}
