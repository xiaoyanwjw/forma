package com.xmut.forma.interfaces.vo.business.credit;

import javax.validation.constraints.NotBlank;

/**
 * 管理改档 HTTP 入参。
 */
public class ChangeTierRequest {

    @NotBlank(message = "目标用户 ID 不能为空")
    private String targetUserId;

    @NotBlank(message = "目标套餐不能为空")
    private String targetTier;

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(String targetTier) {
        this.targetTier = targetTier;
    }
}
