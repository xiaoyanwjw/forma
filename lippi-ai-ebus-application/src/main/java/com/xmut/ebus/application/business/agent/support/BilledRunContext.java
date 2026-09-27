package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;

import java.util.Collections;
import java.util.Map;

/**
 * {@code streamBilledRun} 管道可变上下文：前置/后置处理器可读写 view 与业务载荷。
 */
public final class BilledRunContext {

    private final GenerationRunContext run;
    private Map<String, Object> projectedView = Collections.emptyMap();
    private Map<String, Object> businessPayload = Collections.emptyMap();

    public BilledRunContext(GenerationRunContext run) {
        this.run = run;
    }

    public GenerationRunContext getRun() {
        return run;
    }

    public SkillRunProfile getProfile() {
        return run.getProfile();
    }

    public Map<String, Object> getProjectedView() {
        return projectedView;
    }

    public void setProjectedView(Map<String, Object> projectedView) {
        this.projectedView = projectedView != null
                ? projectedView
                : Collections.<String, Object>emptyMap();
    }

    public Map<String, Object> getBusinessPayload() {
        return businessPayload;
    }

    public void setBusinessPayload(Map<String, Object> businessPayload) {
        this.businessPayload = businessPayload != null
                ? businessPayload
                : Collections.<String, Object>emptyMap();
    }
}
