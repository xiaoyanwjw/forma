package com.xmut.lims.pi.agent.graph;

/**
 * 无条件直连边。
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
