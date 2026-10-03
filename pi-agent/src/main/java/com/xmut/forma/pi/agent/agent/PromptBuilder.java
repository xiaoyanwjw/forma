package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.ai.message.Message;

import java.util.List;

/**
 * 系统提示组装端口。
 * 功能描述：组装可缓存 system 三段并在发出前清洗。
 * 关键设计：chat messages 由 AgentTurn 维护，不归本端口。
 */
public interface PromptBuilder {

    /**
     * 格式化消息列表（对齐 Hermes {@code prompt_builder.format}）。
     */
    List<Message> format(Message system, List<Message> messages);

    /**
     * 三段文本 {@code {stable, context, variable}}。
     */
    SystemPromptStable stable(SystemPromptInput input);

    /**
     * 完整可缓存 system 字符串 = {@link SystemPromptInput#format()}。
     */
    Message system(SystemPromptInput input);

}
