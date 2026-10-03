package com.xmut.forma.pi.ai.model.decorator;

import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.model.TokenConsumer;
import com.xmut.forma.pi.ai.model.port.ModelCallRecord;
import com.xmut.forma.pi.ai.model.port.ModelCallRecorder;

import java.time.Duration;
import java.time.Instant;

/**
 * 审计装饰器 <b>[Lippi]</b>。
 */
public final class AuditedModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final ModelCallRecorder recorder;

    public AuditedModelProvider(ModelProvider delegate, ModelCallRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder != null ? recorder : ModelCallRecorder.NOOP;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        Instant start = Instant.now();
        try {
            ModelResponse response = delegate.complete(request);
            recorder.record(success(request, start));
            return response;
        } catch (RuntimeException e) {
            recorder.record(failure(request, start, e));
            throw e;
        }
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        Instant start = Instant.now();
        try {
            delegate.stream(request, new TokenConsumer() {
                @Override
                public void onTextDelta(String delta) {
                    consumer.onTextDelta(delta);
                }

                @Override
                public void onComplete(ModelResponse response) {
                    recorder.record(success(request, start));
                    consumer.onComplete(response);
                }
            });
        } catch (RuntimeException e) {
            recorder.record(failure(request, start, e));
            throw e;
        }
    }

    private static ModelCallRecord success(ModelRequest request, Instant start) {
        return base(request, start)
                .status(ModelCallRecord.STATUS_SUCCESS)
                .build();
    }

    private static ModelCallRecord failure(ModelRequest request, Instant start, Throwable e) {
        return base(request, start)
                .status(ModelCallRecord.STATUS_FAILED)
                .errorMessage(e != null ? e.getMessage() : null)
                .build();
    }

    private static ModelCallRecord.ModelCallRecordBuilder base(ModelRequest request, Instant start) {
        long latency = Duration.between(start, Instant.now()).toMillis();
        return ModelCallRecord.builder()
                .traceId(request != null ? request.getTraceId() : null)
                .sessionId(request != null ? request.getSessionId() : null)
                .useCase(request != null ? request.getUseCase() : null)
                .runId(request != null ? request.getRunId() : null)
                .latencyMs(latency)
                .occurredAt(start);
    }
}
