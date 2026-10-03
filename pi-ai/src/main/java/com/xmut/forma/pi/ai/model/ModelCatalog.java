package com.xmut.forma.pi.ai.model;

import java.util.Set;

/**
 * 用例 → 模型描述注册表 <b>[Lippi]</b>。
 *
 * <p>对齐上游 {@code resolve_runtime_provider} 的部分语义（useCase 扩展）。
 */
public interface ModelCatalog {

    /**
     * 解析用例。
     *
     * @throws UnsupportedModelException 未注册时
     */
    ModelDescriptor resolve(String useCase);

    /** 已注册用例集合。 */
    Set<String> registeredUseCases();
}
