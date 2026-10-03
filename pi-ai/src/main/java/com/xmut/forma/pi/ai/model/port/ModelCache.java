package com.xmut.forma.pi.ai.model.port;

import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;

import java.util.Optional;

/**
 * 模型响应缓存反向端口 <b>[Lippi]</b>。
 *
 * <p><b>与业务缓存语义分离（NFR11）</b>：本端口仅缓存 LLM {@link ModelResponse}，
 * 业务域缓存（方案摘要、报告片段等）不得共用同一 key 空间或 TTL 语义。
 */
public interface ModelCache {

    Optional<ModelResponse> get(String cacheKey);

    void put(String cacheKey, ModelResponse response, int ttlSeconds);

    /** 无操作：永不命中、不写入。 */
    ModelCache NOOP = new ModelCache() {
        @Override
        public Optional<ModelResponse> get(String cacheKey) {
            return Optional.empty();
        }

        @Override
        public void put(String cacheKey, ModelResponse response, int ttlSeconds) {
            // no-op
        }
    };

    /**
     * 简易缓存键：useCase + sessionId + 全量消息摘要 + temperature/maxTokens（测试 / 默认用）。
     *
     * <p>优先纳入末条 <em>user</em> 内容；同时哈希整段 transcript，避免仅末条碰撞。
     */
    static String simpleKey(ModelRequest request) {
        if (request == null) {
            return "null";
        }
        String useCase = request.getUseCase() != null ? request.getUseCase() : "";
        String session = request.getSessionId() != null ? request.getSessionId() : "";
        StringBuilder transcript = new StringBuilder();
        String lastUser = "";
        if (request.getMessages() != null) {
            for (Message m : request.getMessages()) {
                if (m == null) {
                    continue;
                }
                transcript.append(m.getRole()).append(':')
                        .append(m.getContent() != null ? m.getContent() : "").append('|');
                if (m.getToolCallId() != null) {
                    transcript.append("id=").append(m.getToolCallId()).append('|');
                }
                if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    transcript.append("tc=").append(m.getToolCalls().size()).append('|');
                }
                if ("user".equals(m.getRole()) && m.getContent() != null) {
                    lastUser = m.getContent();
                }
            }
        }
        transcript.append("tu=").append(lastUser).append('|');
        transcript.append("temp=").append(request.getTemperature()).append('|');
        transcript.append("max=").append(request.getMaxTokens());
        return useCase + "|" + session + "|" + transcript.toString().hashCode();
    }
}
