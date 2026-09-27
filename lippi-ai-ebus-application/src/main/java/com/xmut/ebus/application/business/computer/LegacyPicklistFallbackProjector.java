package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.support.PicklistViewProjector;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transition: skill-bound picklist artifact without raw view → domain projector.
 * Remove once Skills emit dual-track {@code view}.
 */
@Component
public class LegacyPicklistFallbackProjector implements ComputerViewProjector {

    private final PicklistViewProjector picklistViewProjector;

    public LegacyPicklistFallbackProjector(PicklistViewProjector picklistViewProjector) {
        this.picklistViewProjector = picklistViewProjector;
    }

    @Override
    public boolean supports(ViewProjectContext context) {
        return context != null
                && context.isSkillBound()
                && context.getRawView() == null
                && context.getArtifact() instanceof PicklistArtifactDTO;
    }

    @Override
    public Map<String, Object> project(ViewProjectContext context) {
        return picklistViewProjector.project((PicklistArtifactDTO) context.getArtifact());
    }
}
