package com.xmut.lims.pi.agent.graph;

/**
 * 图执行期异常。
 * 功能描述：在超步执行失败时抛出。
 */
public class GraphExecutionException extends RuntimeException {

    public GraphExecutionException(String message) {
        super(message);
    }

    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
