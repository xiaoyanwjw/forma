package com.xmut.ebus.application.business.agent.sse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下发给浏览器的 AD-4 SSE 事件（payload 字段细表可后钉）。
 */
public final class SseEvent {

    private final SseEventName name;
    private final Map<String, Object> data;

    public SseEvent(SseEventName name, Map<String, Object> data) {
        this.name = name;
        this.data = data == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data));
    }

    public static SseEvent of(SseEventName name, Map<String, Object> data) {
        return new SseEvent(name, data);
    }

    public SseEventName getName() {
        return name;
    }

    public String getWireName() {
        return name.wireName();
    }

    public Map<String, Object> getData() {
        return data;
    }
}
