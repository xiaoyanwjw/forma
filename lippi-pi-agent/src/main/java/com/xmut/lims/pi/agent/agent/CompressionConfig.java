package com.xmut.lims.pi.agent.agent;

import lombok.Builder;
import lombok.Value;

/**
 * L2 ContextCompressor 阈值配置（Story 51-8）。
 *
 * <p>字符 rough estimate：{@code approxTokens ≈ chars / 4}（非精确 tokenizer）。
 */
@Value
@Builder(toBuilder = true)
public class CompressionConfig {

    /** 关闭时行为等同 {@link ContextCompressor#NOOP}。 */
    @Builder.Default
    boolean enabled = true;

    /**
     * system + messages 总字符超此阈值则压 history（≈12k tokens @ /4）。
     */
    @Builder.Default
    int maxPromptChars = 48_000;

    /** 尾部保留消息条数（≥1）。 */
    @Builder.Default
    int protectLastK = 8;

    /** Context 段（agents+hermes）硬上限。 */
    @Builder.Default
    int contextMaxChars = 4_000;

    /** 中间折叠摘要消息前缀（可测）。 */
    @Builder.Default
    String summaryPrefix = "[context_summary]\n";

    /** deterministic digest 每条消息内容上限。 */
    @Builder.Default
    int digestPerMessageChars = 120;

    /** deterministic digest 总长上限。 */
    @Builder.Default
    int digestMaxChars = 2_000;

    /** Phase0：尾外过长 tool 结果改为一行摘要的阈值（字符）；≤0 关闭。 */
    @Builder.Default
    int toolResultMaxChars = 800;

    public static CompressionConfig defaults() {
        return CompressionConfig.builder().build();
    }

    /** 有效 K：至少 1。 */
    public int effectiveProtectLastK() {
        return protectLastK < 1 ? 1 : protectLastK;
    }
}
