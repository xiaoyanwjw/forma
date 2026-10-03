package com.xmut.forma.pi.agent.event;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 生命周期事件总线。
 * 功能描述：提供 observe 只读扇出与 on 类型化归约。
 * 关键设计：emit 顺序固定：先 observe 后 on。
 */
public interface PiEventBus extends Emitter {

    /**
     * 只读订阅。在 {@link #emit} 时同步调用（先于 on），保证顺序。
     *
     * <p>处理器须非阻塞（禁 I/O / 等待）。异常只打日志，不中断后续。
     *
     * @return 关闭即退订
     */
    AutoCloseable subscribe(Consumer<PiEvent> handler);

    /**
     * 注册可变处理器。emit 时在 observe 之后同步调用；返回值按类型归约。
     *
     * @return 关闭即退订
     */
    AutoCloseable register(PiEventType type, Function<PiEvent, Object> handler);

    /** 清空全部 observe / on。 */
    void clear();
}
