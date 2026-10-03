package com.xmut.forma.infrastructure.persistence.mybatis.mapper;

import com.xmut.forma.infrastructure.persistence.mybatis.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface UserMapper {

    int insert(UserPO user);

    int updateUsername(@Param("bizId") String bizId,
                       @Param("username") String username,
                       @Param("updatedAt") Instant updatedAt);

    int updatePasswordHash(@Param("bizId") String bizId,
                           @Param("passwordHash") String passwordHash,
                           @Param("updatedAt") Instant updatedAt);

    UserPO selectById(@Param("id") String id);

    UserPO selectByUsername(@Param("username") String username);

    UserPO selectByEmail(@Param("email") String email);

    UserPO selectByUsernameOrEmail(@Param("account") String account);
}
