package com.xmut.forma.domain.business.artifact.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成果类型码（与 {@code forma_artifact.artifact_type} 对齐）。
 * 码来自 SKILL.md {@code persistAs} 或落库字符串。
 * 仅无 Skill 草稿 {@code chat} 由本类排除会话历史；场景中间稿用 Skill {@code hideFromHistory}。
 */
public final class ArtifactType {

    /** 无 Skill 聊天草稿。 */
    public static final String CODE_CHAT = "chat";

    private static final List<String> CHAT_ONLY = Collections.singletonList(CODE_CHAT);

    private static final ConcurrentHashMap<String, ArtifactType> BY_CODE = new ConcurrentHashMap<String, ArtifactType>();

    private final String code;
    private final boolean sessionHistory;

    private ArtifactType(String code, boolean sessionHistory) {
        this.code = code;
        this.sessionHistory = sessionHistory;
    }

    public String getCode() {
        return code;
    }

    public boolean isSessionHistory() {
        return sessionHistory;
    }

    /** 平台级排除：仅 chat。 */
    public static List<String> internalCodes() {
        return CHAT_ONLY;
    }

    public static ArtifactType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("artifact type code required");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("artifact type code required");
        }
        ArtifactType existing = BY_CODE.get(normalized);
        if (existing != null) {
            return existing;
        }
        boolean history = !CODE_CHAT.equals(normalized);
        ArtifactType created = new ArtifactType(normalized, history);
        ArtifactType raced = BY_CODE.putIfAbsent(normalized, created);
        return raced != null ? raced : created;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArtifactType)) {
            return false;
        }
        return code.equals(((ArtifactType) other).code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
