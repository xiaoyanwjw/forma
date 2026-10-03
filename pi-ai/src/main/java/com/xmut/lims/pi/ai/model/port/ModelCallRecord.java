package com.xmut.lims.pi.ai.model.port;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 一次模型调用审计记录 <b>[Lippi]</b>。
 */
@Value
@Builder
public class ModelCallRecord {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    String traceId;
    String sessionId;
    String useCase;
    String runId;
    Long latencyMs;
    String status;
    String errorMessage;
    Instant occurredAt;
}
