package com.xmut.forma.extension.tool.view;

import java.util.Map;

/**
 * 场景模块为单个 skill 提供的视图 enrich SPI（注册到 {@link ViewEnricherComposite}）。
 *
 * <p>实现放在各场景 Maven 模块，勿在 core 按 skillId 写死清单。
 */
public interface ViewEnricher {

    /** 绑定的 skill id（与 SKILL.md / pack.yaml 一致）。 */
    String skillId();

    /**
     * @param artifact 已是可写深拷贝；可原地改并返回
     */
    Map<String, Object> enrich(Map<String, Object> artifact);
}
