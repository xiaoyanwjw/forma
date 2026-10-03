package com.xmut.forma.application.business.computer;

import java.util.Collections;
import java.util.Map;

/**
 * Input for {@link ComputerViewProjector} strategies.
 */
public final class ViewProjectContext {

    private final boolean skillBound;
    private final String finalResponse;
    private final Map<String, Object> rawView;
    private final Object artifact;

    private ViewProjectContext(boolean skillBound,
                               String finalResponse,
                               Map<String, Object> rawView,
                               Object artifact) {
        this.skillBound = skillBound;
        this.finalResponse = finalResponse;
        this.rawView = rawView == null ? null : Collections.unmodifiableMap(rawView);
        this.artifact = artifact;
    }

    public boolean isSkillBound() {
        return skillBound;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public Map<String, Object> getRawView() {
        return rawView;
    }

    public Object getArtifact() {
        return artifact;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean skillBound;
        private String finalResponse;
        private Map<String, Object> rawView;
        private Object artifact;

        public Builder skillBound(boolean skillBound) {
            this.skillBound = skillBound;
            return this;
        }

        public Builder finalResponse(String finalResponse) {
            this.finalResponse = finalResponse;
            return this;
        }

        public Builder rawView(Map<String, Object> rawView) {
            this.rawView = rawView;
            return this;
        }

        public Builder artifact(Object artifact) {
            this.artifact = artifact;
            return this;
        }

        public ViewProjectContext build() {
            return new ViewProjectContext(skillBound, finalResponse, rawView, artifact);
        }
    }
}
