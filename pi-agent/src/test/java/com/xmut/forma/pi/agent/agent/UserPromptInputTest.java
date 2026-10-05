package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserPromptInputTest {

    private static UserModifier prefixLastUserWithX() {
        return new UserModifier() {
            @Override
            public List<Message> apply(List<Message> messages) {
                if (messages == null || messages.isEmpty()) {
                    return messages;
                }
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if (m != null && "user".equals(m.getRole())) {
                        List<Message> out = new ArrayList<Message>(messages);
                        out.set(i, Message.user("X" + m.getContent()));
                        return out;
                    }
                }
                return messages;
            }
        };
    }

    @Test
    void format_withoutModifier_returnsSameContents() {
        Message earlier = Message.user("earlier");
        Message assistant = Message.assistant("mid", Collections.emptyList());
        Message last = Message.user("原文");
        List<Message> input = new ArrayList<Message>(Arrays.asList(earlier, assistant, last));

        List<Message> out = UserPromptInput.builder().messages(input).build().format();

        assertThat(out).hasSize(3);
        assertThat(out.get(0).getContent()).isEqualTo("earlier");
        assertThat(out.get(1).getContent()).isEqualTo("mid");
        assertThat(out.get(2).getContent()).isEqualTo("原文");
        assertThat(input.get(2).getContent()).isEqualTo("原文");
    }

    @Test
    void apply_runsUserModifier_onBuild() {
        Message earlier = Message.user("earlier");
        Message last = Message.user("原文");
        List<Message> input = Arrays.asList(earlier, last);

        ContextModifier modifier = ContextModifier.empty();
        modifier.setUser(prefixLastUserWithX());

        List<Message> out = UserPromptInput.builder()
                .messages(input)
                .apply(modifier)
                .build()
                .format();

        assertThat(out.get(0).getContent()).isEqualTo("earlier");
        assertThat(out.get(1).getContent()).isEqualTo("X原文");
        assertThat(last.getContent()).isEqualTo("原文");
    }

    @Test
    void apply_nullModifier_noop() {
        Message user = Message.user("hi");
        List<Message> input = Collections.singletonList(user);

        List<Message> out = UserPromptInput.builder()
                .messages(input)
                .apply(null)
                .build()
                .format();

        assertThat(out.get(0).getContent()).isEqualTo("hi");
    }

    @Test
    void apply_nullUser_noop() {
        Message user = Message.user("hi");
        List<Message> input = Collections.singletonList(user);

        List<Message> out = UserPromptInput.builder()
                .messages(input)
                .apply(ContextModifier.empty())
                .build()
                .format();

        assertThat(out.get(0).getContent()).isEqualTo("hi");
    }
}
