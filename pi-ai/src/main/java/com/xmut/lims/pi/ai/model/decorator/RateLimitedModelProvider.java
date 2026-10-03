package com.xmut.lims.pi.ai.model.decorator;

import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.TokenConsumer;
import com.xmut.lims.pi.ai.model.UnsupportedModelException;
import com.xmut.lims.pi.ai.model.port.ModelRateLimit;

/**
 * 限流装饰器 <b>[Lippi]</b>：链序最外层建议为 RateLimited。
 *
 * <p>业务配额 vs 本装饰器：本类只拦截模型调用；业务配额应在应用层单独处理。
 */
public final class RateLimitedModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final ModelRateLimit rateLimit;
    private final ModelCatalog catalog;

    public RateLimitedModelProvider(ModelProvider delegate, ModelRateLimit rateLimit, ModelCatalog catalog) {
        this.delegate = delegate;
        this.rateLimit = rateLimit != null ? rateLimit : ModelRateLimit.NOOP;
        this.catalog = catalog;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        Integer daily = resolveDailyLimit(request);
        if (daily != null && daily > 0) {
            // blank sessionId → "_" so quota still applies (shared anonymous bucket)
            String sessionId = rateLimitKey(request);
            String useCase = request != null ? request.getUseCase() : null;
            if (!rateLimit.acquire(sessionId, useCase, daily)) {
                throw new ModelRateLimitExceededException(
                        "rate limit exceeded: sessionId=" + sessionId + " useCase=" + useCase
                                + " dailyLimit=" + daily);
            }
        }
        return delegate.complete(request);
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        Integer daily = resolveDailyLimit(request);
        if (daily != null && daily > 0) {
            String sessionId = rateLimitKey(request);
            String useCase = request.getUseCase();
            if (!rateLimit.acquire(sessionId, useCase, daily)) {
                throw new ModelRateLimitExceededException(
                        "rate limit exceeded: sessionId=" + sessionId + " useCase=" + useCase
                                + " dailyLimit=" + daily);
            }
        }
        delegate.stream(request, consumer);
    }

    /** 空白 sessionId 归一为 {@code "_"}，避免 fail-open。 */
    static String rateLimitKey(ModelRequest request) {
        String sessionId = request != null ? request.getSessionId() : null;
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "_";
        }
        return sessionId.trim();
    }

    private Integer resolveDailyLimit(ModelRequest request) {
        if (request == null || catalog == null || request.getUseCase() == null) {
            return null;
        }
        try {
            ModelDescriptor desc = catalog.resolve(request.getUseCase());
            return desc != null ? desc.getDailyQuotaPerTenant() : null;
        } catch (UnsupportedModelException e) {
            return null;
        }
    }
}
