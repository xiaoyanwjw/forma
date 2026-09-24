package com.xmut.lims.pi.agent.resource;

import java.util.Optional;

/**
 * 扫描并冻结 Agent Context（skills / tools / prompts）。
 *
 * <p>架构概念名 ResourceLoader；Java 接口刻意避开 Spring
 * {@code org.springframework.core.io.ResourceLoader}。
 */
public interface PiResourceLoader {

    AgentResourceSnapshot snapshot();

    /**
     * 重扫 {@code classpath*:prompts/*.md}。
     */
    void reload();

    Optional<PromptTemplate> findPrompt(String name);

    /**
     * 展开 {@code /name} 模板或 {@code /skill:id}；未知斜杠原样返回。
     */
    SlashExpansion expandSlash(String text);
}
