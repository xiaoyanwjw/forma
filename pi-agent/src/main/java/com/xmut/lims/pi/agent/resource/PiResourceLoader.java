package com.xmut.lims.pi.agent.resource;

import java.util.Optional;

/**
 * Agent 资源加载端口。
 * 功能描述：扫描并冻结 skills / tools / prompts 等 Agent Context。
 */
public interface PiResourceLoader {

    AgentResourceSnapshot snapshot();

    /**
     * 重扫 {@code classpath*:prompts/*.md}。
     * Skill 仅启动装载（seal 后不可热更）。
     */
    void reload();

    Optional<PromptTemplate> findPrompt(String name);

    /**
     * 展开 {@code /name} 模板或 {@code /skill:id}；未知斜杠原样返回。
     */
    SlashExpansion expandSlash(String text);
}
