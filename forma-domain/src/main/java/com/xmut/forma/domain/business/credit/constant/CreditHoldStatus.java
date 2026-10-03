package com.xmut.forma.domain.business.credit.constant;

/**
 * 预占状态：活跃 / 已结算 / 已释放。
 */
public enum CreditHoldStatus {

    ACTIVE,
    SETTLED,
    RELEASED;

    public static CreditHoldStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("hold status required");
        }
        return CreditHoldStatus.valueOf(code.trim().toUpperCase());
    }
}
