package com.xmut.forma.interfaces.config;

import com.xmut.forma.interfaces.ratelimit.AgentRateLimitInterceptor;
import com.xmut.forma.interfaces.ratelimit.AuthRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor authRateLimitInterceptor;
    private final AgentRateLimitInterceptor agentRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/register", "/api/v1/auth/login");
        registry.addInterceptor(agentRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/agent/runs",
                        "/api/v1/agent/runs/picklist",
                        "/api/v1/agent/runs/listing",
                        "/api/v1/agent/runs/*/resume");
    }
}
