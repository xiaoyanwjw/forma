package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.ai.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 入图前 user/chat 轴原料。
 * 功能描述：builder → apply(ContextModifier) → build → format 得到最终 messages。
 */
public final class UserPromptInput {

    private final List<Message> messages;

    UserPromptInput(List<Message> messages) {
        this.messages = Collections.unmodifiableList(new ArrayList<Message>(messages));
    }

    public static Builder builder() {
        return new Builder();
    }

    public UserPromptInput apply(ContextModifier modifier) {
        return toBuilder().apply(modifier).build();
    }

    /** build 时已落好的 messages 的不可变副本。 */
    public List<Message> format() {
        return Collections.unmodifiableList(new ArrayList<Message>(messages));
    }

    public Builder toBuilder() {
        return builder().messages(messages);
    }

    public static final class Builder {

        private List<Message> messages;
        private UserModifier user;

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder apply(ContextModifier modifier) {
            if (modifier != null && modifier.getUser() != null) {
                this.user = modifier.getUser();
            }
            return this;
        }

        public UserPromptInput build() {
            List<Message> out = messages == null
                    ? Collections.<Message>emptyList()
                    : messages;
            if (user != null) {
                List<Message> applied = user.apply(out);
                out = applied != null ? applied : out;
            }
            return new UserPromptInput(out);
        }
    }
}
