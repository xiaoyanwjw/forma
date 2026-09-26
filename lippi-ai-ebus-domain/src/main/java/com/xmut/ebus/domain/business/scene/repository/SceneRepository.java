package com.xmut.ebus.domain.business.scene.repository;

import com.xmut.ebus.domain.business.scene.model.Scene;

import java.util.List;

/**
 * SceneCatalog 持久化端口（场景元数据唯一写者 AD-14；近端仅只读列表）。
 */
public interface SceneRepository {

    /**
     * 按 {@code sort_order} 升序返回全部场景行（含灰卡）。
     */
    List<Scene> listOrdered();
}
