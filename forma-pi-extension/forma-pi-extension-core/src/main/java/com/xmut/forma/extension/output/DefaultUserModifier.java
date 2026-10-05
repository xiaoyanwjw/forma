package com.xmut.forma.extension.output;

import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.ai.message.Message;

import java.util.List;

/**
 * 普通 Skill 的 user 前缀。
 * 功能描述：把交付槽位 reminder 贴到最后一条 user。
 */
public final class DefaultUserModifier implements UserModifier {

    private final String modifiedString;

    public DefaultUserModifier(String modifiedString) {
        this.modifiedString = modifiedString;
    }

    @Override
    public List<Message> apply(List<Message> messages) {
        return TurnReminder.rewriteLastUser(messages, modifiedString);
    }
}
