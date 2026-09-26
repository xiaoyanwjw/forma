package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.PicklistItemPO;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PicklistPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PicklistMapper {

    int insert(PicklistPO picklist);

    int insertItem(PicklistItemPO item);

    PicklistPO selectByBizId(@Param("bizId") String bizId);

    PicklistPO selectByRunId(@Param("runId") String runId);

    List<PicklistItemPO> selectItemsByPicklistId(@Param("picklistId") String picklistId);
}
