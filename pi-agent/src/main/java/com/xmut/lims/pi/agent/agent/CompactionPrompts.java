package com.xmut.lims.pi.agent.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 会话压缩 LLM 提示词（对齐上游 Pi coding-agent compaction；本仓将首次/增量合并为一份指令）。
 * 功能描述：从 classpath {@code pi/compaction/*.md} 加载 system / 结构化指令，供 {@link DefaultContextCompressor} 使用。
 * 关键设计：有无 {@code <previous-summary>} 由同一份 user 指令内两小节说明；摘要正文语言跟随压缩前对话。
 */
public final class CompactionPrompts {

    private static final String SYSTEM_RESOURCE = "pi/compaction/summarization-system.md";
    private static final String SUMMARIZATION_RESOURCE = "pi/compaction/summarization.md";

    /** 对齐 Pi {@code SUMMARIZATION_SYSTEM_PROMPT}（本仓追加语言跟随约束）。 */
    public static final String SUMMARIZATION_SYSTEM_PROMPT = load(SYSTEM_RESOURCE);

    /**
     * 首次创建与增量更新共用的 user 指令（内含「有无 previous-summary」两小节）。
     */
    public static final String SUMMARIZATION_PROMPT = load(SUMMARIZATION_RESOURCE);

    private CompactionPrompts() {
    }

    /**
     * 组装发给模型的 user 正文（conversation + 可选 previous-summary + 统一指令）。
     */
    public static String toUserPrompt(String conversationText, String previousSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("<conversation>\n")
                .append(conversationText == null ? "" : conversationText)
                .append("\n</conversation>\n\n");
        if (previousSummary != null && !previousSummary.isEmpty()) {
            sb.append("<previous-summary>\n")
                    .append(previousSummary)
                    .append("\n</previous-summary>\n\n");
        }
        sb.append(SUMMARIZATION_PROMPT);
        return sb.toString();
    }

    private static String load(String classpathLocation) {
        InputStream in = CompactionPrompts.class.getClassLoader().getResourceAsStream(classpathLocation);
        if (in == null) {
            throw new IllegalStateException("classpath resource missing: " + classpathLocation);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("failed to load classpath resource: " + classpathLocation, e);
        }
    }
}
