package com.xmut.ebus.domain.identity.repository;

import com.xmut.ebus.domain.identity.model.User;

import java.time.Instant;
import java.util.Optional;

/**
 * 用户持久化端口（Identity 唯一写）。
 */
public interface UserRepository {

    void save(User user);

    /**
     * 按业务用户 ID 更新用户名与 updated_at。
     *
     * @return {@code true} 若更新了至少一行；{@code false} 若无匹配行
     */
    boolean updateUsername(String userId, String username, Instant updatedAt);

    Optional<User> findById(String id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * 按用户名或邮箱查找（登录账号）。
     */
    Optional<User> findByUsernameOrEmail(String account);
}
