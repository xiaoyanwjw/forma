package com.xmut.lims.pi.agent.resource;

import lombok.Value;

/**
 * 斜杠展开结果。未展开时 {@code text} 原样、{@code skillId} 空。
 */
@Value
public class SlashExpansion {

    String text;
    String skillId;
    boolean expanded;

    public static SlashExpansion unchanged(String text) {
        return new SlashExpansion(text, null, false);
    }

    public static SlashExpansion of(String text, String skillId) {
        return new SlashExpansion(text, skillId, true);
    }
}
