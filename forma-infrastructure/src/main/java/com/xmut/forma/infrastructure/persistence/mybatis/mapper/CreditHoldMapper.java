package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.CreditHoldPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface CreditHoldMapper {

    int insert(CreditHoldPO hold);

    CreditHoldPO selectById(@Param("id") String id);

    int updateStatusIfActive(@Param("id") String id,
                             @Param("userId") String userId,
                             @Param("newStatus") String newStatus,
                             @Param("updatedAt") Instant updatedAt);

    List<CreditHoldPO> listSettledByUserId(@Param("userId") String userId, @Param("limit") int limit);
}
