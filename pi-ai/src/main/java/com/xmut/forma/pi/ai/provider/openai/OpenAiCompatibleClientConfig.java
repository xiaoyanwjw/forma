package com.xmut.forma.pi.ai.provider.openai;

/**
 * OpenAI 兼容 HTTP 客户端配置。
 */
public interface OpenAiCompatibleClientConfig {

    String getBaseUrl();

    String getApiKey();

    Integer getTimeoutMs();

    String getProviderName();
}
