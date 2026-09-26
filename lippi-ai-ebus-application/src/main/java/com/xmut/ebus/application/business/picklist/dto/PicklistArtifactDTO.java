package com.xmut.ebus.application.business.picklist.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 选品成果 DTO（落库后回传 / SSE artifact_ready）。
 */
public final class PicklistArtifactDTO {

    private final String picklistId;
    private final String runId;
    private final String templateId;
    private final String disclaimer;
    private final String assumptions;
    private final List<PicklistItemDTO> items;

    public PicklistArtifactDTO(String picklistId,
                               String runId,
                               String templateId,
                               String disclaimer,
                               String assumptions,
                               List<PicklistItemDTO> items) {
        this.picklistId = picklistId;
        this.runId = runId;
        this.templateId = templateId;
        this.disclaimer = disclaimer;
        this.assumptions = assumptions;
        this.items = items == null
                ? Collections.<PicklistItemDTO>emptyList()
                : Collections.unmodifiableList(new ArrayList<PicklistItemDTO>(items));
    }

    public String getPicklistId() {
        return picklistId;
    }

    public String getRunId() {
        return runId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public String getAssumptions() {
        return assumptions;
    }

    public List<PicklistItemDTO> getItems() {
        return items;
    }

    public static final class PicklistItemDTO {
        private final String title;
        private final String priceBand;
        private final String reason;
        private final String differentiation;
        private final String demand;
        private final String competition;
        private final String margin;
        private final String risk;

        public PicklistItemDTO(String title,
                               String priceBand,
                               String reason,
                               String differentiation,
                               String demand,
                               String competition,
                               String margin,
                               String risk) {
            this.title = title;
            this.priceBand = priceBand;
            this.reason = reason;
            this.differentiation = differentiation;
            this.demand = demand;
            this.competition = competition;
            this.margin = margin;
            this.risk = risk;
        }

        public String getTitle() {
            return title;
        }

        public String getPriceBand() {
            return priceBand;
        }

        public String getReason() {
            return reason;
        }

        public String getDifferentiation() {
            return differentiation;
        }

        public String getDemand() {
            return demand;
        }

        public String getCompetition() {
            return competition;
        }

        public String getMargin() {
            return margin;
        }

        public String getRisk() {
            return risk;
        }
    }
}
