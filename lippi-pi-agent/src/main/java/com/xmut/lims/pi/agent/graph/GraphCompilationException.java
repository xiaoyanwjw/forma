package com.xmut.lims.pi.agent.graph;

/**
 * 图编译期异常。
 * 功能描述：在拓扑/配置非法时抛出。
 */
public class GraphCompilationException extends RuntimeException {

    public GraphCompilationException(String message) {
        super(message);
    }
}
