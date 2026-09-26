package com.xmut.ebus.domain.business.scene.repository;

import com.xmut.ebus.domain.business.scene.model.Scene;

import java.util.List;
import java.util.Optional;

/**
 * SceneCatalog 持久化端口（场景元数据唯一写者 AD-14；近端仅只读列表）。
 */
public interface SceneRepository {

    /**
     * 按 {@code sort_order} 升序返回全部场景行（含灰卡）。
     */
    List<Scene> listOrdered();

    /** 按业务 UUID（{@code biz_id}）查单行。 */
    Optional<Scene> findByBizId(String bizId);

    /** 按稳定 {@code sceneCode} 查单行。 */
    Optional<Scene> findBySceneCode(String sceneCode);
}
