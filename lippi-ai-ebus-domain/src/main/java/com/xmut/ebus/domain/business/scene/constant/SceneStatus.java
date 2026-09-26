package com.xmut.ebus.domain.business.scene.constant;

/**
 * 场景开放状态（画廊亮卡 / 灰卡）。
 */
public enum SceneStatus {

    AVAILABLE,
    COMING_SOON;

    public static SceneStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("scene status required");
        }
        return SceneStatus.valueOf(code.trim().toUpperCase());
    }
}
