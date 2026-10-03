package com.xmut.lims.pi.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 目录端口（对标官方 {@code ResourceLoader#getSkills()} 背后的技能清单）。
 * 功能描述：登记、解析与列举 Skill。
 * 关键设计：与 ToolCatalog 对称；生产默认可禁运行时突变；按 id 索引。
 */
public interface SkillCatalog {

    /**
     * 校验并入册。同 {@code id} 默认拒绝覆盖。
     * 生产 {@code allow-runtime-mutation=false} 时运行时调用拒绝（启动 bootstrap 除外）。
     */
    void register(Skill skill);

    /**
     * 启动装载窗口：绕过运行时突变门禁，仍走完整校验。
     * {@link #sealBootstrap()} 之后再调用一律拒绝。
     */
    void registerBootstrap(Skill skill);

    /**
     * 关闭启动装载窗口。之后 {@link #registerBootstrap} 拒绝（即使 mutation=false）。
     */
    void sealBootstrap();

    /**
     * 按 id 精确查询。
     */
    Optional<Skill> get(String id);

    /**
     * 取该 id 的当前指针：最近一次成功 {@link #register}/{@link #registerBootstrap} 更新的条目。
     */
    Optional<Skill> resolve(String id);

    /**
     * 全部已注册 Skill 的不可变快照（顺序：注册序）。与 {@link com.xmut.lims.pi.agent.tool.ToolCatalog#all()} 对称。
     */
    List<Skill> all();

    /**
     * 按 {@link Skill#getSceneCode()} 过滤（精确匹配 trim 后的 sceneCode）。
     */
    List<Skill> listByScene(String sceneCode);

    /**
     * 移除 id；仅 {@code allow-runtime-mutation=true} 时允许。
     */
    void unregister(String id);

    /**
     * 显式覆盖同 id；仅 {@code allow-runtime-mutation=true} 时允许。
     */
    void replace(Skill skill);
}
