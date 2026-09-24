package com.xmut.lims.pi.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ProtocolRoutingModelProvider;
import com.xmut.lims.pi.ai.model.StubModelProvider;
import com.xmut.lims.pi.ai.model.decorator.ModelDecorators;
import com.xmut.lims.pi.ai.provider.VendorRoutingModelProvider;
import com.xmut.lims.pi.ai.provider.dashscope.DashScopeProperties;
import com.xmut.lims.pi.ai.provider.deepseek.DeepSeekProperties;
import com.xmut.lims.pi.ai.provider.openai.OpenAiCompatibleHttpClient;
import com.xmut.lims.pi.ai.provider.openai.OpenAiCompatibleModelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册 DashScope / DeepSeek {@link ModelProvider}。不依赖 pi-agent 源码。
 *
 * <p>有 Key 的厂商才进路由表。若一个都没有，注册 {@link StubModelProvider}（勿 return null）。
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.xmut.lims.pi.agent.config.PiAutoConfiguration")
@EnableConfigurationProperties({DashScopeProperties.class, DeepSeekProperties.class})
public class PiAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ModelCatalog.class)
    public ModelCatalog piAiModelCatalog() {
        return InMemoryModelCatalog.defaultsWithCertificateOcr();
    }

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    public ModelProvider piAiModelProvider(ModelCatalog catalog,
                                           DashScopeProperties dashScopeProperties,
                                           DeepSeekProperties deepSeekProperties,
                                           ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        Map<String, ModelProvider> vendors = new LinkedHashMap<String, ModelProvider>();
        if (dashScopeProperties != null && dashScopeProperties.isEnabled()) {
            OpenAiCompatibleHttpClient http = new OpenAiCompatibleHttpClient(dashScopeProperties);
            vendors.put(DashScopeProperties.NAME, new OpenAiCompatibleModelProvider(http, mapper, catalog));
        }
        if (deepSeekProperties != null && deepSeekProperties.isEnabled()) {
            OpenAiCompatibleHttpClient http = new OpenAiCompatibleHttpClient(deepSeekProperties);
            vendors.put(DeepSeekProperties.NAME, new OpenAiCompatibleModelProvider(http, mapper, catalog));
        }
        if (vendors.isEmpty()) {
            // 无 Key：不返回 null（避免 Spring 注入 null ModelProvider）；使用可运行 Stub。
            ModelProvider stub = new StubModelProvider();
            return new ProtocolRoutingModelProvider(stub, catalog);
        }

        ModelProvider routed = new VendorRoutingModelProvider(vendors, catalog);
        ModelProvider protocol = new ProtocolRoutingModelProvider(routed, catalog);
        return ModelDecorators.wrapWithNoopPorts(protocol, catalog);
    }

}
