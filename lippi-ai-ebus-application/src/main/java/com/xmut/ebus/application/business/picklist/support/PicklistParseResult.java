package com.xmut.ebus.application.business.picklist.support;

import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;

import java.util.Collections;
import java.util.Map;

/**
 * Dual-track parse outcome: business command for persist/settle, optional Skill {@code view} for Computer.
 */
public final class PicklistParseResult {

    private final PersistPicklistCommand command;
    private final Map<String, Object> rawView;

    public PicklistParseResult(PersistPicklistCommand command, Map<String, Object> rawView) {
        this.command = command;
        this.rawView = rawView == null ? null : Collections.unmodifiableMap(rawView);
    }

    public PersistPicklistCommand getCommand() {
        return command;
    }

    /**
     * Skill-emitted Computer document map, or {@code null} when absent (legacy flat / artifact-only).
     */
    public Map<String, Object> getRawView() {
        return rawView;
    }
}
