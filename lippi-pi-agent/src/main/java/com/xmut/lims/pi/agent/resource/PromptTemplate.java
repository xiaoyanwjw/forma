package com.xmut.lims.pi.agent.resource;

import lombok.Value;

/**
 * 斜杠 Prompt 模板。
 * 功能描述：对应 prompts/*.md；name 无前导 / 与 .md 后缀。
 */
@Value
public class PromptTemplate {

    String name;
    String description;
    String body;
}
