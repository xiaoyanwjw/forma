package com.xmut.lims.pi.agent.resource;

import lombok.Value;

/**
 * prompts/*.md 斜杠模板（Story 51-14）。
 *
 * <p>{@code name} 无前导 {@code /}、无 {@code .md}；{@code body} 已去掉 frontmatter。
 */
@Value
public class PromptTemplate {

    String name;
    String description;
    String body;
}
