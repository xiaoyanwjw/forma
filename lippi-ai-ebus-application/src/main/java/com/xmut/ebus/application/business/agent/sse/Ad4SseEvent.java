package com.xmut.ebus.application.business.agent.sse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下发给浏览器的 AD-4 SSE 事件（payload 字段细表可后钉）。
 */
public final class Ad4SseEvent {

    private final Ad4EventName name;
    private final Map<String, Object> data;

    public Ad4SseEvent(Ad4EventName name, Map<String, Object> data) {
        this.name = name;
        this.data = data == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data));
    }

    public static Ad4SseEvent of(Ad4EventName name, Map<String, Object> data) {
        return new Ad4SseEvent(name, data);
    }

    public Ad4EventName getName() {
        return name;
    }

    public String getWireName() {
        return name.wireName();
    }

    public Map<String, Object> getData() {
        return data;
    }
}
