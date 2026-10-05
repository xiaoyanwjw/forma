package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.extension.output.TurnReminder;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.ai.message.Message;

import java.util.List;

/**
 * 上架素材的 user 前缀。
 * 功能描述：把本轮 plan 或 exec 交付槽位 reminder 贴到最后一条 user。
 * 关键设计：prefix 由 Extension 选好路径后传入；apply 只调用 {@link TurnReminder#prefixLastUser}。
 */
public final class EcommerceSkuUserModifier implements UserModifier {

    private final String prefix;

    public EcommerceSkuUserModifier(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<Message> apply(List<Message> messages) {
        return TurnReminder.prefixLastUser(messages, prefix);
    }
}
