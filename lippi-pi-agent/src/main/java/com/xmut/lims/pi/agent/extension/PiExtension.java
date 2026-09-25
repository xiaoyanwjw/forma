package com.xmut.lims.pi.agent.extension;

/**
 * 可发现的运行时扩展。
 * 功能描述：以 Spring 组件或 SPI 形式挂到事件总线。
 * 关键设计：无方法式钩子；command / before_agent_start / tool 前后等一律走 bus。
 */
public interface PiExtension extends PiExtensionRegistrar {
}
