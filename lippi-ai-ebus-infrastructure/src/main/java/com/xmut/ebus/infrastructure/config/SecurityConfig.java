package com.xmut.ebus.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.infrastructure.identity.JwtAuthenticationFilter;
import com.xmut.ebus.infrastructure.persistence.mybatis.InstantTypeHandler;
import com.xmut.ebus.infrastructure.web.TraceIdFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 无状态安全配置（无 TenantContext / 无 Redis 会话）。
 */
@Configuration
@EnableWebSecurity
@MapperScan("com.xmut.ebus.infrastructure.persistence.mybatis.mapper")
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ConfigurationCustomizer mybatisInstantTypeHandler() {
        return configuration -> configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
    }

    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus());
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(ErrorCode.UNAUTHORIZED.getHttpStatus(), ErrorCode.UNAUTHORIZED.getMessage()));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TraceIdFilter traceIdFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint jsonAuthenticationEntryPoint) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling().authenticationEntryPoint(jsonAuthenticationEntryPoint)
                .and()
                .authorizeRequests()
                .antMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/actuator/health",
                        "/actuator/info"
                ).permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
