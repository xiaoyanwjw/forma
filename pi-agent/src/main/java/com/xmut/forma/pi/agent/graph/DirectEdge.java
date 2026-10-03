package com.xmut.forma.pi.agent.graph;

/**
 * 无条件直连边。
 * 功能描述：固定连接两个节点。
 */
final class DirectEdge {

    private final String from;
    private final String to;

    DirectEdge(String from, String to) {
        this.from = from;
        this.to = to;
    }

    String getFrom() {
        return from;
    }

    String getTo() {
        return to;
    }
}
