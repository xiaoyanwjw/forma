package com.xmut.forma.common.cas;

import com.xmut.forma.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class CasRetryAspect {

    @Around("@annotation(casRetry)")
    public Object around(ProceedingJoinPoint pjp, CasRetry casRetry) throws Throwable {
        int maxAttempts = casRetry.maxAttempts();
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (CasConflictException conflict) {
                if (attempt >= maxAttempts) {
                    throw new BusinessException(casRetry.exhaustedErrorCode(), casRetry.exhaustedMessage());
                }
            }
        }
        throw new IllegalStateException("CAS retry loop exited unexpectedly");
    }
}
