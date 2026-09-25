package com.xmut.lims.pi.agent.resource;

import lombok.Value;

/**
 * 斜杠展开结果。
 * 功能描述：表示展开后的 text / skillId；未展开时 text 原样。
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
