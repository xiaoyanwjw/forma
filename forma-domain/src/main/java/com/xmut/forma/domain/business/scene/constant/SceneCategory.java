package com.xmut.forma.domain.business.scene.constant;

/**
 * 画廊一级分类码（稳定；展示名由前端映射）。
 */
public enum SceneCategory {

    TECH("tech"),
    ECOMMERCE("ecommerce"),
    CONTENT("content"),
    SPORTS("sports"),
    /** 出行：行程、探店、周末怎么玩（不是到店团购卖点） */
    LIFE("life");

    private final String code;

    SceneCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static SceneCategory fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("scene category required");
        }
        String normalized = code.trim().toLowerCase();
        for (SceneCategory category : values()) {
            if (category.code.equals(normalized)) {
                return category;
            }
        }
        throw new IllegalArgumentException("unknown scene category: " + code);
    }
}
