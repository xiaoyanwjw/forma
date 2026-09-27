package com.xmut.ebus.application.business.computer;

import java.util.Map;

/**
 * Strategy for producing a Computer {@code view} document.
 */
public interface ComputerViewProjector {

    boolean supports(ViewProjectContext context);

    /**
     * @return ComputerDocument map; must not be null when {@link #supports} is true
     */
    Map<String, Object> project(ViewProjectContext context);
}
