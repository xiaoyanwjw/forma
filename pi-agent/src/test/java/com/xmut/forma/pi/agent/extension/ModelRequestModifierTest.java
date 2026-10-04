package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRequestModifierTest {

    private static final String PREFIX = "<reminder>\nX\n</reminder>\n\n";

    @Test
    void apply_prefixes_only_the_last_user_and_does_not_mutate_input() {
        Message earlier = Message.user("earlier");
        Message assistant = Message.assistant("mid", Collections.emptyList());
        Message last = Message.user("原文");
        List<Message> input = new ArrayList<Message>(Arrays.asList(earlier, assistant, last));

        List<Message> out = new ModelRequestModifier(PREFIX).apply(input);

        assertThat(out).isNotSameAs(input);
        assertThat(out.get(0)).isSameAs(earlier);
        assertThat(out.get(1)).isSameAs(assistant);
        assertThat(out.get(2)).isNotSameAs(last);
        assertThat(out.get(2).getContent()).isEqualTo(PREFIX + "原文");
        assertThat(input.get(2).getContent()).isEqualTo("原文");
        assertThat(earlier.getContent()).isEqualTo("earlier");
    }

    @Test
    void apply_prefixes_last_user_when_an_assistant_follows() {
        Message user = Message.user("ask");
        Message assistant = Message.assistant("ans", Collections.emptyList());
        List<Message> input = Arrays.asList(user, assistant);

        List<Message> out = new ModelRequestModifier("P\n").apply(input);

        assertThat(out.get(0).getContent()).isEqualTo("P\nask");
        assertThat(out.get(1)).isSameAs(assistant);
        assertThat(user.getContent()).isEqualTo("ask");
    }

    @Test
    void apply_is_idempotent_when_content_already_starts_with_reminder() {
        String already = "  <reminder>\nold\n</reminder>\n\nbody";
        List<Message> input = new ArrayList<Message>(Collections.singletonList(Message.user(already)));

        List<Message> out = new ModelRequestModifier(PREFIX).apply(input);

        assertThat(out.get(0).getContent()).isEqualTo(already);
        assertThat(out.get(0)).isSameAs(input.get(0));
    }

    @Test
    void apply_null_and_empty_return_as_is() {
        ModelRequestModifier mod = new ModelRequestModifier(PREFIX);

        assertThat(mod.apply(null)).isNull();
        List<Message> empty = Collections.emptyList();
        assertThat(mod.apply(empty)).isEmpty();
        assertThat(mod.apply(empty)).isSameAs(empty);
    }

    @Test
    void apply_without_user_returns_input() {
        Message assistant = Message.assistant("only", Collections.emptyList());
        List<Message> input = Collections.singletonList(assistant);

        List<Message> out = new ModelRequestModifier(PREFIX).apply(input);

        assertThat(out).containsExactly(assistant);
        assertThat(out.get(0)).isSameAs(assistant);
    }

    @Test
    void apply_blank_prefix_returns_input() {
        Message user = Message.user("hi");
        List<Message> input = Collections.singletonList(user);

        assertThat(new ModelRequestModifier(null).apply(input).get(0)).isSameAs(user);
        assertThat(new ModelRequestModifier("  \n").apply(input).get(0)).isSameAs(user);
        assertThat(ModelRequestModifier.empty().getLastUserPrefix()).isNull();
        assertThat(ModelRequestModifier.empty().apply(input).get(0).getContent()).isEqualTo("hi");
    }
}
