package com.xmut.ebus.application.business.agent.sse;

import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pi 内部事件 → AD-4 闭合事件名。
 * <p>
 * 声明全七名（本类 + {@link Ad4EventName}）；空跑路径不发出 {@code artifact_ready}/{@code run_settled}。
 * {@code run_started}/{@code run_failed} 由编排层显式发送，不从此处对 AGENT_* 映射，避免与 Pi 内部名混用。
 */
public final class PiEventToAd4Mapper {

    private PiEventToAd4Mapper() {
    }

    /**
     * 映射可流式进度类事件；无法映射则 empty（调用方勿把 Pi 名直接下发）。
     */
    public static Optional<Ad4SseEvent> mapEvent(PiEvent event) {
        if (event == null || event.getType() == null) {
            return Optional.empty();
        }
        PiEventType type = event.getType();
        if (type == PiEventType.MESSAGE_UPDATE) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.message_delta, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_START) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.tool_started, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_END) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.tool_finished, payloadMap(event.getPayload())));
        }
        return Optional.empty();
    }

    /** 供测试与类型声明：AD-4 七名全集。 */
    public static Ad4EventName[] declaredEventNames() {
        return Ad4EventName.values();
    }

    private static Map<String, Object> payloadMap(Object payload) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (payload != null) {
            data.put("payload", String.valueOf(payload));
        }
        return data.isEmpty() ? Collections.<String, Object>emptyMap() : data;
    }
}
