package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.message.Message;

import java.util.List;

/**
 * L2 提示组装器：对齐上游 {@code prompt_builder} / Prompt Assembly。
 *
 * <p>只负责可缓存 system 三段与发出前清洗；chat {@code messages} 由 AgentTurn 维护。
 *
 * <p>类型名必须是 {@code PromptBuilder}；禁止 {@code PromptAssembler}。
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
