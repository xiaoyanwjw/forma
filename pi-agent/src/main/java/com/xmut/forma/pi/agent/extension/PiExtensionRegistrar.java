package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.agent.event.PiEventBus;

/**
 * 扩展注册接口。
 * 功能描述：在启动期把 on/observe 处理器挂到 Session 持有的 bus。
 */
public interface PiExtensionRegistrar {

    void register(PiEventBus bus);
}
