package com.xmut.lims.pi.agent.graph;

/**
 * 图执行期错误。
 */
public class GraphExecutionException extends RuntimeException {

    public GraphExecutionException(String message) {
        super(message);
    }

    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
