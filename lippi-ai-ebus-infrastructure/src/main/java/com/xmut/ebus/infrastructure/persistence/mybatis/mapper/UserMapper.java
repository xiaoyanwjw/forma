package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int insert(UserPO user);

    UserPO selectById(@Param("id") String id);

    UserPO selectByUsername(@Param("username") String username);

    UserPO selectByEmail(@Param("email") String email);

    UserPO selectByUsernameOrEmail(@Param("account") String account);
}
