package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves a Computer {@code view}: ordered strategy chain (first {@code supports} wins), fail closed.
 */
public class ComputerViewResolver {

    public static final String MSG_VIEW_UNAVAILABLE = "成果视图不可用，请重试";

    private final List<ComputerViewProjector> projectors;

    public ComputerViewResolver(List<ComputerViewProjector> projectors) {
        this.projectors = projectors == null
                ? Collections.<ComputerViewProjector>emptyList()
                : Collections.unmodifiableList(new ArrayList<ComputerViewProjector>(projectors));
    }

    /**
     * @return projected ComputerDocument map
     * @throws BusinessException when no strategy produces a view
     */
    public Map<String, Object> resolve(ViewProjectContext context) {
        if (context == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_VIEW_UNAVAILABLE);
        }
        for (ComputerViewProjector projector : projectors) {
            if (projector.supports(context)) {
                Map<String, Object> view = projector.project(context);
                if (view != null && !view.isEmpty()) {
                    return view;
                }
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_VIEW_UNAVAILABLE);
    }
}
