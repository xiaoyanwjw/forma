package com.xmut.lims.pi.agent.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 会话压缩 LLM 提示词（对齐上游 Pi coding-agent compaction）。
 * 功能描述：从 classpath {@code pi/compaction/*.md} 加载 system / 结构化指令，供 {@link DefaultContextCompressor} 使用。
 * 关键设计：文案对齐 pi-mono SUMMARIZATION_*；摘要正文语言跟随压缩前对话；/compact 接线后置。
 */
public final class CompactionPrompts {

    private static final String SYSTEM_RESOURCE = "pi/compaction/summarization-system.md";
    private static final String SUMMARIZATION_RESOURCE = "pi/compaction/summarization.md";
    private static final String UPDATE_RESOURCE = "pi/compaction/update-summarization.md";

    /** 对齐 Pi {@code SUMMARIZATION_SYSTEM_PROMPT}（本仓追加语言跟随约束）。 */
    public static final String SUMMARIZATION_SYSTEM_PROMPT = load(SYSTEM_RESOURCE);

    /** 对齐 Pi {@code SUMMARIZATION_PROMPT}（首次摘要）。 */
    public static final String SUMMARIZATION_PROMPT = load(SUMMARIZATION_RESOURCE);

    /** 对齐 Pi {@code UPDATE_SUMMARIZATION_PROMPT}（已有摘要时合并增量）。 */
    public static final String UPDATE_SUMMARIZATION_PROMPT = load(UPDATE_RESOURCE);

    private CompactionPrompts() {
    }

    /**
     * 组装发给模型的 user 正文（conversation + 可选 previous-summary + 指令）。
     */
    public static String toUserPrompt(String conversationText, String previousSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("<conversation>\n")
                .append(conversationText == null ? "" : conversationText)
                .append("\n</conversation>\n\n");
        if (previousSummary != null && !previousSummary.isEmpty()) {
            sb.append("<previous-summary>\n")
                    .append(previousSummary)
                    .append("\n</previous-summary>\n\n")
                    .append(UPDATE_SUMMARIZATION_PROMPT);
        } else {
            sb.append(SUMMARIZATION_PROMPT);
        }
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
