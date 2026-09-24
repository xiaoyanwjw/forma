package com.xmut.ebus.common.cas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class CasRetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CasRetryAspect casRetryAspect() {
        return new CasRetryAspect();
    }
}
