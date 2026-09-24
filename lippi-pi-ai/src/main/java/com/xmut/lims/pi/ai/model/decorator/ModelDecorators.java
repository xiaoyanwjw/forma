package com.xmut.lims.pi.ai.model.decorator;

import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.port.ModelCache;
import com.xmut.lims.pi.ai.model.port.ModelCallRecorder;
import com.xmut.lims.pi.ai.model.port.ModelRateLimit;

/**
 * Decorator 链工厂 <b>[Lippi]</b>。
 *
 * <p>建议顺序：{@code RateLimited → Cached → Audited → Retried → 底层 Provider}。
 * 非 Hermes 原语。
 */
public final class ModelDecorators {

    private ModelDecorators() {}

    /**
     * 组装标准链；反向端口缺省为 NOOP。
     */
    public static ModelProvider wrap(ModelProvider innermost,
                                     ModelCatalog catalog,
                                     ModelRateLimit rateLimit,
                                     ModelCache cache,
                                     ModelCallRecorder recorder) {
        ModelProvider p = new RetriedModelProvider(innermost, catalog);
        p = new AuditedModelProvider(p, recorder != null ? recorder : ModelCallRecorder.NOOP);
        p = new CachedModelProvider(p, cache != null ? cache : ModelCache.NOOP, catalog);
        p = new RateLimitedModelProvider(p, rateLimit != null ? rateLimit : ModelRateLimit.NOOP, catalog);
        return p;
    }

    /** 仅用 NOOP 反向端口包装。 */
    public static ModelProvider wrapWithNoopPorts(ModelProvider innermost, ModelCatalog catalog) {
        return wrap(innermost, catalog, ModelRateLimit.NOOP, ModelCache.NOOP, ModelCallRecorder.NOOP);
    }
}
