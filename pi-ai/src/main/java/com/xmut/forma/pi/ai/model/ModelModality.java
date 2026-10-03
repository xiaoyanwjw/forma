package com.xmut.forma.pi.ai.model;

/**
 * 模型模态能力描述 <b>[Lippi]</b>。
 *
 * <p>用于 Catalog；<strong>禁止</strong>照搬 agent {@code Capability} 枚举。
 */
public enum ModelModality {

    /** 文本对话补全（对齐 completions）。 */
    CHAT,

    /** 流式输出（方法位可后置）。 */
    STREAM,

    /** 多模态输入（方法位可后置）。 */
    MULTIMODAL
}
