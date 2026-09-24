package com.xmut.lims.pi.ai.provider.deepseek;

import com.xmut.lims.pi.ai.provider.openai.OpenAiCompatibleClientConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.providers.deepseek")
public class DeepSeekProperties implements OpenAiCompatibleClientConfig {

    public static final String NAME = "deepseek";

    private String baseUrl = "https://api.deepseek.com";

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
