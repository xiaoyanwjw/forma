package com.xmut.lims.pi.agent.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Pi Runtime 自动装配入口。
 * 功能描述：经 spring.factories 引入 AgentConfiguration。
 */
@AutoConfiguration
@Import(AgentConfiguration.class)
public class PiAutoConfiguration {
}
