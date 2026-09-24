package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.CreditHoldPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface CreditHoldMapper {

    int insert(CreditHoldPO hold);

    CreditHoldPO selectById(@Param("id") String id);

    int tryClaimFromActive(@Param("id") String id,
                           @Param("userId") String userId,
                           @Param("newStatus") String newStatus,
                           @Param("updatedAt") Instant updatedAt);
}
