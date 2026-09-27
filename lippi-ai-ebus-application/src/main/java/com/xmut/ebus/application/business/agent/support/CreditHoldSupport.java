package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thin credit-hold helpers for GenerationRun orchestration (reserve / settle / release).
 * Does not own Run status transitions.
 */
@Slf4j
@Component
public class CreditHoldSupport {

    private final CreditApplicationService creditService;

    public CreditHoldSupport(CreditApplicationService creditService) {
        this.creditService = creditService;
    }

    public String reserveOne(String userId) {
        return creditService.reserveOne(userId);
    }

    public void settle(String userId, String holdId) {
        creditService.settle(userId, holdId);
    }

    /**
     * @return true only when release succeeds
     */
    public boolean release(String userId, String holdId, String runId) {
        try {
            creditService.release(userId, holdId);
            LoggerUtils.success(log, CreditHoldSupport.class, "release",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId),
                    NameValue.create("runId", runId));
            return true;
        } catch (BusinessException ex) {
            LoggerUtils.error(log, CreditHoldSupport.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占失败",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId));
            return false;
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, CreditHoldSupport.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占异常",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId));
            return false;
        }
    }
}
