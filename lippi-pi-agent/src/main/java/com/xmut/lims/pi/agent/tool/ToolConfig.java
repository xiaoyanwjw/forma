package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;

import java.util.List;
import java.util.Optional;

/**
 * 工具配置端口。
 * 功能描述：提供 Manifest 目录、分级闸门与模型可见投影。
 * 关键设计：未登记/未知 fail-closed；与 SkillConfig 对称。
 */
public interface ToolConfig {

    // —— 与 SkillConfig 对称的目录 API ——

    /**
     * 按 id 精确查询 Manifest。
     */
    Optional<ToolManifest> get(String id);

    /**
     * 取该 id 的当前 Manifest（工具无多版本时等同 {@link #get}）。
     */
    Optional<ToolManifest> resolve(String id);

    /**
     * 全部已入册 Manifest 快照（含 FORBIDDEN）；顺序 = 注册序。
     */
    List<ToolManifest> manifests();

    // —— Tool 特有：闸门 + 投影 ——

    /**
     * 查询工具级别；未注册 / 未知 → {@link ToolLevel#FORBIDDEN}（fail-closed）。
     */
    ToolLevel levelOf(String toolName);

    /**
     * 已注册且非 FORBIDDEN 的 schema 列表，供 {@code AVAILABLE_TOOLS}。
     */
    List<ToolSchema> schemasForModel();

    /**
     * 按白名单投影 schema。
     *
     * <ul>
     *   <li>{@code whitelist == null} → 等同 {@link #schemasForModel()}（不裁剪）</li>
     *   <li>{@code whitelist} 空 → 空列表（不向模型暴露 tools）</li>
     *   <li>非空 → 交集；未知名忽略</li>
     * </ul>
     */
    List<ToolSchema> schemasForModel(java.util.Collection<String> whitelist);

    /**
     * 已注册且非 FORBIDDEN 的 Stable 文本（拼接各 Manifest {@code text}），供 {@code StateKeys.TOOLS}。
     */
    String textForModel();

    /**
     * 按白名单投影 Stable 文本（语义同 {@link #schemasForModel(java.util.Collection)}）。
     */
    String textForModel(java.util.Collection<String> whitelist);

    /**
     * 已注册 handler（FORBIDDEN 以外可执行者）；未知 → empty。
     */
    Optional<ToolHandler> handlerOf(String toolName);
}
