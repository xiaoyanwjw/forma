package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Billing gate: a usable Computer {@code view} must exist before settle.
 */
@Component
public class ComputerViewGate {

    public static final String MSG_VIEW_UNAVAILABLE = "成果视图不可用，请重试";

    private final ViewProjectorChain viewProjectorChain;

    public ComputerViewGate(ViewProjectorChain viewProjectorChain) {
        this.viewProjectorChain = viewProjectorChain;
    }

    /**
     * @return projected ComputerDocument map
     * @throws BusinessException when no strategy produces a view
     */
    public Map<String, Object> requireView(ViewProjectContext context) {
        Optional<Map<String, Object>> projected = viewProjectorChain.project(context);
        if (!projected.isPresent() || projected.get().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_VIEW_UNAVAILABLE);
        }
        return projected.get();
    }
}
