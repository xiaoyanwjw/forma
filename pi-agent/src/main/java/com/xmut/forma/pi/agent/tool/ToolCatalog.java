package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.ai.model.ToolSchema;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 工具目录端口（对标官方 {@code getAllTools} / {@code registerTool} 背后的已装集合）。
 * 功能描述：提供已注册 {@link ToolDefinition} 目录与按名投影。
 * 关键设计：未登记 fail-closed；启用由回合 active 名字集决定。
 */
public interface ToolCatalog {

    /**
     * 按 id 精确查询定义。
     */
    Optional<ToolDefinition> get(String id);

    /**
     * 取该 id 的当前定义（工具无多版本时等同 {@link #get}）。
     */
    Optional<ToolDefinition> resolve(String id);

    /**
     * 全部已入册定义快照；顺序 = 注册序。
     */
    List<ToolDefinition> all();

    /**
     * 是否已注册；空名 / 未知 → false。
     */
    boolean isRegistered(String toolName);

    /**
     * 已注册工具的 schema 列表，供 {@code AVAILABLE_TOOLS}。
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
    List<ToolSchema> schemasForModel(Collection<String> whitelist);

    /**
     * 已注册工具的 Stable 文本（拼接各定义 {@code text}），供 {@code StateKeys.TOOLS}。
     */
    String textForModel();

    /**
     * 按白名单投影 Stable 文本（语义同 {@link #schemasForModel(Collection)}）。
     */
    String textForModel(Collection<String> whitelist);

    /**
     * 已注册 handler；未知或无 handler → empty。
     */
    Optional<ToolHandler> handlerOf(String toolName);
}
