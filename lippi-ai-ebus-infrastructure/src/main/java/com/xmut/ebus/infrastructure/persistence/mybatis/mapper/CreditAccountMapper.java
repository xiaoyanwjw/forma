package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.CreditAccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface CreditAccountMapper {

    int insert(CreditAccountPO account);

    CreditAccountPO selectById(@Param("id") String id);

    CreditAccountPO selectByUserId(@Param("userId") String userId);

    int tryReserve(@Param("id") String id,
                   @Param("amount") int amount,
                   @Param("expectedVersion") int expectedVersion,
                   @Param("updatedAt") Instant updatedAt);

    int trySettle(@Param("id") String id,
                  @Param("amount") int amount,
                  @Param("expectedVersion") int expectedVersion,
                  @Param("updatedAt") Instant updatedAt);

    int tryRelease(@Param("id") String id,
                   @Param("amount") int amount,
                   @Param("expectedVersion") int expectedVersion,
                   @Param("updatedAt") Instant updatedAt);

    int tryApplyMonthlyReset(@Param("id") String id,
                             @Param("balance") int balance,
                             @Param("nextResetAt") Instant nextResetAt,
                             @Param("expectedVersion") int expectedVersion,
                             @Param("updatedAt") Instant updatedAt);
}
