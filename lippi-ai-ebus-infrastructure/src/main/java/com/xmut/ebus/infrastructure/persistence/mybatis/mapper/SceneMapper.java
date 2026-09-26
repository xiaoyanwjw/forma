package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.ScenePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SceneMapper {

    List<ScenePO> selectAllOrdered();

    ScenePO selectByBizId(@Param("bizId") String bizId);

    ScenePO selectBySceneCode(@Param("sceneCode") String sceneCode);
}
