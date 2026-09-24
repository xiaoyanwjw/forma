package com.xmut.ebus.domain.identity.repository;

import com.xmut.ebus.domain.identity.model.User;

import java.util.Optional;

/**
 * 用户持久化端口（Identity 唯一写）。
 */
public interface UserRepository {

    void save(User user);

    Optional<User> findById(String id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * 按用户名或邮箱查找（登录账号）。
     */
    Optional<User> findByUsernameOrEmail(String account);
}
