package com.xmut.forma.pi.ai.provider.dashscope;

import com.xmut.forma.pi.ai.provider.openai.OpenAiCompatibleClientConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.providers.dashscope")
public class DashScopeProperties implements OpenAiCompatibleClientConfig {

    public static final String NAME = "dashscope";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey;

    private Integer timeoutMs = 30_000;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String getProviderName() {
        return NAME;
    }
}
