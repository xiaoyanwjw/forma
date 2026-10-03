package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.CreditTierChangePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CreditTierChangeMapper {

    int insert(CreditTierChangePO change);

    List<CreditTierChangePO> selectByTargetUserId(@Param("targetUserId") String targetUserId);
}
