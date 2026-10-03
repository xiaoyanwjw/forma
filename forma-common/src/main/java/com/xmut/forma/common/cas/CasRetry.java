package com.xmut.forma.common.cas;

import com.xmut.forma.common.exception.ErrorCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CasRetry {

    int maxAttempts() default 8;

    String exhaustedMessage() default "操作冲突，请稍后重试";

    ErrorCode exhaustedErrorCode() default ErrorCode.SYSTEM_ERROR;
}
