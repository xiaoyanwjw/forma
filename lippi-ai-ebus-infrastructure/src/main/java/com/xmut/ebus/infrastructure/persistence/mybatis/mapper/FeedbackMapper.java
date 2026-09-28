package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.FeedbackPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FeedbackMapper {

    int insert(FeedbackPO feedback);

    int updateByBizId(FeedbackPO feedback);

    FeedbackPO selectByBizId(@Param("bizId") String bizId);

    FeedbackPO selectByUserAndArtifact(@Param("userId") String userId, @Param("artifactId") String artifactId);
}
