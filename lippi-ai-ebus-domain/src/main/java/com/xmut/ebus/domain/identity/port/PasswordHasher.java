package com.xmut.ebus.domain.identity.port;

/**
 * 密码哈希端口（实现用 BCrypt；领域不依赖 Spring Security）。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
