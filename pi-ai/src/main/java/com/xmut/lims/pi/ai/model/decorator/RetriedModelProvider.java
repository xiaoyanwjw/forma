package com.xmut.lims.pi.ai.model.decorator;

import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.TokenConsumer;
import com.xmut.lims.pi.ai.model.UnsupportedModelException;

/**
 * 重试装饰器 <b>[Lippi]</b>：链序最内层（紧邻底层 Provider）。
 */
public final class RetriedModelProvider implements ModelProvider {

    private static final int DEFAULT_MAX_ATTEMPTS = 1;

    private final ModelProvider delegate;
    private final ModelCatalog catalog;

    public RetriedModelProvider(ModelProvider delegate, ModelCatalog catalog) {
        this.delegate = delegate;
        this.catalog = catalog;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        int max = resolveMaxTimes(request);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                return delegate.complete(request);
            } catch (RuntimeException e) {
                last = e;
                if (attempt >= max || !isRetryable(e)) {
                    throw e;
                }
            }
        }
        if (last != null) {
            throw last;
        }
        return delegate.complete(request);
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        int max = resolveMaxTimes(request);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            final boolean[] emitted = {false};
            try {
                delegate.stream(request, new TokenConsumer() {
                    @Override
                    public void onTextDelta(String delta) {
                        emitted[0] = true;
                        consumer.onTextDelta(delta);
                    }

                    @Override
                    public void onComplete(ModelResponse response) {
                        consumer.onComplete(response);
                    }
                });
                return;
            } catch (RuntimeException e) {
                last = e;
                if (emitted[0] || attempt >= max || !isRetryable(e)) {
                    throw e;
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private int resolveMaxTimes(ModelRequest request) {
        if (request == null || catalog == null || request.getUseCase() == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        try {
            ModelDescriptor desc = catalog.resolve(request.getUseCase());
            Integer max = desc != null ? desc.getMaxAttempts() : null;
            return max != null && max > 0 ? max : DEFAULT_MAX_ATTEMPTS;
        } catch (UnsupportedModelException e) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    static boolean isRetryable(RuntimeException e) {
        if (e instanceof ModelRateLimitExceededException) {
            return false;
        }
        // 业务非法 / 配置错误不重试
        if (e instanceof IllegalArgumentException
                || e instanceof IllegalStateException
                || e instanceof UnsupportedModelException) {
            return false;
        }
        // 仅网络 / 超时类临时错误可重试
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof java.io.IOException) {
                return true;
            }
            String name = cur.getClass().getName();
            if (name.contains("Timeout")
                    || name.contains("ConnectException")
                    || name.contains("SocketException")
                    || name.contains("UnknownHostException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
