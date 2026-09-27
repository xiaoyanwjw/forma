package com.xmut.ebus.application.business.computer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ordered strategy chain: first {@link ComputerViewProjector#supports} wins.
 */
public final class ViewProjectorChain {

    private final List<ComputerViewProjector> projectors;

    public ViewProjectorChain(List<ComputerViewProjector> projectors) {
        this.projectors = projectors == null
                ? Collections.<ComputerViewProjector>emptyList()
                : Collections.unmodifiableList(new ArrayList<ComputerViewProjector>(projectors));
    }

    public Optional<Map<String, Object>> project(ViewProjectContext context) {
        if (context == null) {
            return Optional.empty();
        }
        for (ComputerViewProjector projector : projectors) {
            if (projector.supports(context)) {
                Map<String, Object> view = projector.project(context);
                if (view != null) {
                    return Optional.of(view);
                }
            }
        }
        return Optional.empty();
    }
}
