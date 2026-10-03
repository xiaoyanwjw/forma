package com.xmut.forma.infrastructure.persistence.repository.identity;

import com.xmut.forma.domain.identity.model.User;
import com.xmut.forma.domain.identity.repository.UserRepository;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.UserMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public void save(User user) {
        userMapper.insert(toPo(user));
    }

    @Override
    public boolean updateUsername(String userId, String username, Instant updatedAt) {
        return userMapper.updateUsername(userId, username, updatedAt) > 0;
    }

    @Override
    public boolean updatePasswordHash(String userId, String passwordHash, Instant updatedAt) {
        return userMapper.updatePasswordHash(userId, passwordHash, updatedAt) > 0;
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectByUsername(username)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectByEmail(email)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String account) {
        return Optional.ofNullable(userMapper.selectByUsernameOrEmail(account)).map(this::toDomain);
    }

    private UserPO toPo(User user) {
        UserPO po = new UserPO();
        po.setBizId(user.getId());
        po.setUsername(user.getUsername());
        po.setEmail(user.getEmail());
        po.setPasswordHash(user.getPasswordHash());
        po.setCreatedAt(user.getCreatedAt());
        po.setUpdatedAt(user.getUpdatedAt());
        return po;
    }

    private User toDomain(UserPO po) {
        User user = new User();
        user.setId(po.getBizId());
        user.setUsername(po.getUsername());
        user.setEmail(po.getEmail());
        user.setPasswordHash(po.getPasswordHash());
        user.setCreatedAt(po.getCreatedAt());
        user.setUpdatedAt(po.getUpdatedAt());
        return user;
    }
}
