package com.xmut.forma.domain.identity.port;

/**
 * JWT 签发/校验端口（claims 最小：sub=userId、iat/exp）。
 */
public interface JwtTokenPort {

    String generateToken(String userId);

    boolean validateToken(String token);

    String getUserIdFromToken(String token);
}
