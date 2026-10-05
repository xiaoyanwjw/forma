package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.ai.message.Message;

import java.util.List;

public interface UserModifier {
    List<Message> apply(List<Message> messages);
}
