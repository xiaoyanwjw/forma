package com.xmut.ebus.domain.business.artifact.model;

/**
 * 通用成果类型（与 {@code ebus_artifact.artifact_type} 对齐）。
 */
public enum ArtifactType {

    PICKLIST("picklist"),
    SKU("sku");

    private final String code;

    ArtifactType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ArtifactType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("artifact type code required");
        }
        String normalized = code.trim();
        for (ArtifactType type : values()) {
            if (type.code.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown artifact type: " + code);
    }
}
