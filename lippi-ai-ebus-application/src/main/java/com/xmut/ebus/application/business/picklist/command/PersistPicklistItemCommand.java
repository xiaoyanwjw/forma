package com.xmut.ebus.application.business.picklist.command;

import lombok.Builder;
import lombok.Getter;

/**
 * 选品条目写入参。
 */
@Getter
@Builder
public class PersistPicklistItemCommand {

    private final String title;
    private final String priceBand;
    private final String painPoint;
    private final String angle;
    private final String diff;
    private final String niche;
    private final String demand;
    private final String competition;
    private final String margin;
    private final String risk;
    /** Marketplace origin URL; required https. Stored in artifact JSON payload. */
    private final String sourceUrl;
}
