package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.MediaObjectPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaObjectMapper {

    int insert(MediaObjectPO media);

    MediaObjectPO selectByBizId(@Param("bizId") String bizId);

    int deleteByBizId(@Param("bizId") String bizId);
}
