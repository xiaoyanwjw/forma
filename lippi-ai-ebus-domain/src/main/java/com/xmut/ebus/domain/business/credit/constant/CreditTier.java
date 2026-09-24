package com.xmut.ebus.domain.business.credit.constant;

/**
 * 套餐档与月额度（AD-5：免费 20 / Pro 200 / Plus 600）。
 */
public enum CreditTier {

    FREE(20),
    PRO(200),
    PLUS(600);

    private final int monthlyQuota;

    CreditTier(int monthlyQuota) {
        this.monthlyQuota = monthlyQuota;
    }

    public int getMonthlyQuota() {
        return monthlyQuota;
    }

    public static CreditTier fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("tier code required");
        }
        return CreditTier.valueOf(code.trim().toUpperCase());
    }
}
