package com.xmut.lims.pi.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * L3 Skill 配置端口（与 {@link com.xmut.lims.pi.agent.tool.ToolConfig} 对称；Story 51-9 / FR22）。
 *
 * <p>≠ 上游 {@code ~/.hermes/skills} 可自改；≠ agent {@code ToolRegistry}；≠ {@code SkillRouter}（51-10）。
 *
 * <p>共享目录 API：{@link #get} / {@link #resolve} / {@link #manifests}。
 */
public interface SkillConfig {

    /**
     * 校验并入册。同 {@code id}+{@code version} 默认拒绝覆盖。
     * 生产 {@code allow-runtime-mutation=false} 时运行时调用拒绝（启动 bootstrap 除外）。
     */
    void register(SkillManifest manifest);

    /**
     * 启动装载窗口：绕过运行时突变门禁，仍走完整校验。
     * {@link #sealBootstrap()} 之后再调用一律拒绝。
     */
    void registerBootstrap(SkillManifest manifest);

    /**
     * 关闭启动装载窗口。之后 {@link #registerBootstrap} 拒绝（即使 mutation=false）。
     */
    void sealBootstrap();

    /**
     * 按 id+version 精确查询（与 ToolConfig#get 对称；Skill 多版本故带 version）。
     */
    Optional<SkillManifest> get(String id, String version);

    /**
     * 取该 id 的「当前」版本：最近一次成功 {@link #register}/{@link #registerBootstrap} 更新的指针。
     */
    Optional<SkillManifest> resolve(String id);

    /**
     * 全部已注册 manifest 的不可变快照（顺序：注册序）。与 {@code ToolConfig#manifests} 同名。
     */
    List<SkillManifest> manifests();

    /**
     * 移除 id+version；仅 {@code allow-runtime-mutation=true} 时允许。
     */
    void unregister(String id, String version);

    /**
     * 显式覆盖同 id+version；仅 {@code allow-runtime-mutation=true} 时允许。
     */
    void replace(SkillManifest manifest);
}
