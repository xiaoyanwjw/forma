package com.xmut.lims.pi.agent.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Pi Runtime 自动装配入口（Story 51-1 / 51-12）。
 *
 * <p>经 {@code META-INF/spring.factories} 启用；应用侧注入 {@link com.xmut.lims.pi.agent.session.AgentSession}。
 */
@AutoConfiguration
@Import(AgentConfiguration.class)
public class PiAutoConfiguration {
}
