package com.xmut.ebus.domain.business.picklist.constant;

/**
 * 近端默认品类模板身份与字段长度上限（对齐 010_ebus_picklist.sql）。
 */
public final class PicklistDefaults {

    public static final String TEMPLATE_ID = "domestic-generic-default";

    public static final int MIN_ITEMS = 8;
    public static final int MAX_ITEMS = 12;

    public static final int MAX_TITLE = 256;
    public static final int MAX_PRICE_BAND = 64;
    public static final int MAX_REASON = 1024;
    public static final int MAX_DIM = 512;
    public static final int MAX_DISCLAIMER = 512;
    public static final int MAX_ASSUMPTIONS = 1024;

    private PicklistDefaults() {
    }
}
