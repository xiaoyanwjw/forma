package com.xmut.forma.common.cas;

/**
 * 乐观锁 / 条件更新未命中，可由 {@link CasRetry} 切面重试。
 * 非业务失败：业务拒绝请抛 {@link com.xmut.forma.common.exception.BusinessException}。
 */
public class CasConflictException extends RuntimeException {

    public CasConflictException() {
        super("CAS conflict");
    }

    public CasConflictException(String message) {
        super(message);
    }
}
