package com.xmut.forma.application.business.scene.pack;

import java.util.Optional;

/**
 * 场景元数据目录：按 sceneCode 查 {@link SceneMeta}。
 */
public interface SceneMetaCatalog {

    Optional<SceneMeta> find(String sceneCode);
}
