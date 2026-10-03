package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;

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
        throw new BusinessException(ErrorCode.PARAM_INVALID, diagnoseFailure(context));
    }

    /**
     * Human-readable failure reason for skill-bound / missing-view cases (shown on STATUS).
     */
    static String diagnoseFailure(ViewProjectContext context) {
        if (context == null) {
            return MSG_VIEW_UNAVAILABLE;
        }
        if (context.isSkillBound() && context.getRawView() == null) {
            String finalResponse = context.getFinalResponse();
            if (!StringUtils.hasText(finalResponse)) {
                return "模型未返回终态内容，无法生成成果视图。请重试。";
            }
            String trimmed = finalResponse.trim();
            if (!trimmed.contains("\"view\"")) {
                return "模型终态缺少 view 字段，无法生成成果视图。请展开「模型输出」核对 JSON。";
            }
            return "模型终态未能解析出合法 view（需含 version / title / format / content 的 version=2 文档）。"
                    + "常见原因：JSON 被截断或不完整。请展开「模型输出」核对，或缩短选品条数后重试。";
        }
        return MSG_VIEW_UNAVAILABLE;
    }
}
