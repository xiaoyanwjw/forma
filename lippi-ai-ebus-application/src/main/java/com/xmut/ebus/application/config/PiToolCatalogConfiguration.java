package com.xmut.ebus.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyOkHttpTransport;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyTaobaoSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.FallbackSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.MockSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.ModelSkuReranker;
import com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.application.business.agent.tool.sku.SkuReranker;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchPort;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearcher;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ModelXhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteFetchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteFetchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearcher;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.xmut.lims.pi.agent.config.AgentConfiguration;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.Tool;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.ToolDefinitionJsonLoader;
import com.xmut.lims.pi.agent.tool.ToolHandlerAutoBinder;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.OverlayModelCatalog;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary {@link ToolCatalog} for Adam: {@code read_skill} + {@code search_sku} + {@code search_xhs_note}
 * + {@code fetch_xhs_note} + {@code ask_human} + workspace {@code write_file} / {@code read_file} / {@code bash}.
 *
 * <p>Not {@code @ConditionalOnMissingBean} — this bean must replace pi-agent's default
 * catalog so {@code search_sku} is registered at startup.
 */
@Configuration
@EnableConfigurationProperties({SkuSearchProperties.class, XhsNoteSearchProperties.class, XhsNoteFetchProperties.class})
public class PiToolCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PiToolCatalogConfiguration.class);

    /**
     * Live {@link ModelCatalog} used by pi-ai routing. pi-agent / pi-ai only register
     * {@code pi.default}; SKU rerank needs {@code ebus.sku.rerank} on the same catalog.
     */
    @Primary
    @Bean
    public ModelCatalog modelCatalog(SkuSearchProperties skuProps, XhsNoteSearchProperties xhsProps) {
        return overlayRerankUseCases(skuProps, xhsProps);
    }

    static ModelCatalog overlayWithSkuRerank(SkuSearchProperties props) {
        return overlayRerankUseCases(props, new XhsNoteSearchProperties());
    }

    static ModelCatalog overlayRerankUseCases(SkuSearchProperties skuProps, XhsNoteSearchProperties xhsProps) {
        OverlayModelCatalog overlay = new OverlayModelCatalog(InMemoryModelCatalog.defaults());
        overlay.putOverride(
                resolveUseCase(skuProps == null ? null : skuProps.getSearcher().getRerankUseCase(), "ebus.sku.rerank"),
                rerankDescriptor(resolveUseCase(
                        skuProps == null ? null : skuProps.getSearcher().getRerankUseCase(), "ebus.sku.rerank")));
        overlay.putOverride(
                resolveUseCase(xhsProps == null ? null : xhsProps.getSearcher().getRerankUseCase(), "ebus.xhs.rerank"),
                rerankDescriptor(resolveUseCase(
                        xhsProps == null ? null : xhsProps.getSearcher().getRerankUseCase(), "ebus.xhs.rerank")));
        return overlay;
    }

    private static String resolveUseCase(String configured, String fallback) {
        return StringUtils.hasText(configured) ? configured : fallback;
    }

    static ModelDescriptor rerankDescriptor(String useCase) {
        return InMemoryModelCatalog.defaultChatDescriptor().toBuilder()
                .useCase(useCase)
                .temperature(0.0)
                .maxTokens(512)
                .build();
    }

    @Bean
    public SkuSearchPort skuSearchPort(SkuSearchProperties props) {
        MockSkuSearchClient mock = new MockSkuSearchClient();
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return mock;
        }
        String actorId = props.getApify().getActorId();
        String token = props.getApify().getToken();
        if (token == null || token.trim().isEmpty()) {
            LoggerUtils.error(
                    log,
                    PiToolCatalogConfiguration.class,
                    "skuSearchPort",
                    "missing_token",
                    NameValue.create("client", "apify"),
                    NameValue.create("actorId", actorId),
                    NameValue.create("queryLen", 0),
                    NameValue.create("hitCount", 0));
            return mock;
        }
        ApifyTaobaoSkuSearchClient apify =
                new ApifyTaobaoSkuSearchClient(props, new ApifyOkHttpTransport());
        return new FallbackSkuSearchClient(apify, mock, actorId);
    }

    @Bean
    public SkuReranker skuReranker(ObjectProvider<ModelProvider> models, SkuSearchProperties props) {
        ModelProvider mp = models.getIfAvailable();
        if (mp == null) {
            return SkuReranker.identity();
        }
        return new ModelSkuReranker(mp, props);
    }

    @Bean
    public SkuSearcher skuSearcher(SkuSearchPort port, SkuSearchProperties props, SkuReranker reranker) {
        return new SkuSearcher(port, props, reranker);
    }

    @Bean
    public XhsNoteSearchPort xhsNoteSearchPort(XhsNoteSearchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockXhsNoteSearchClient();
        }
        return new ApifyXhsNoteSearchClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public XhsNoteReranker xhsNoteReranker(ObjectProvider<ModelProvider> models, XhsNoteSearchProperties props) {
        ModelProvider mp = models.getIfAvailable();
        if (mp == null) {
            return XhsNoteReranker.identity();
        }
        return new ModelXhsNoteReranker(mp, props);
    }

    @Bean
    public XhsNoteSearcher xhsNoteSearcher(XhsNoteSearchPort port, XhsNoteSearchProperties props, XhsNoteReranker reranker) {
        return new XhsNoteSearcher(port, props, reranker);
    }

    @Bean
    public XhsNoteFetchPort xhsNoteFetchPort(XhsNoteFetchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockXhsNoteFetchClient();
        }
        return new ApifyXhsNoteFetchClient(props, new ApifyOkHttpTransport());
    }

    @Primary
    @Bean
    public ToolCatalog toolCatalog(
            SkillCatalog skillCatalog,
            SkuSearcher skuSearcher,
            XhsNoteSearcher xhsNoteSearcher,
            XhsNoteFetchPort xhsNoteFetchPort,
            ResourcePatternResolver resourcePatternResolver,
            BeanFactory beanFactory) {
        List<ToolDefinition> scanned = ToolDefinitionJsonLoader.load(resourcePatternResolver);
        List<ToolBinding> coded = new ArrayList<ToolBinding>(
                ToolHandlerAutoBinder.bindFromManifests(scanned, beanFactory));
        coded.add(searchSkuTool(skuSearcher).getBinding());
        coded.add(searchXhsNoteTool(xhsNoteSearcher).getBinding());
        coded.add(fetchXhsNoteTool(xhsNoteFetchPort).getBinding());
        InMemoryToolCatalog catalog = InMemoryToolCatalog.merge(scanned, coded);
        AgentConfiguration.requireHandlers(catalog, scanned);
        return catalog;
    }

    static Tool searchSkuTool(SkuSearcher skuSearcher) {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "搜索关键词，例如 香薰");
        ObjectNode pageSize = properties.putObject("pageSize");
        pageSize.put("type", "integer");
        pageSize.put("description", "每页条数，默认 10，最大 20");
        parameters.putArray("required").add("query");
        ToolSchema schema = ToolSchema.builder()
                .name(SearchSkuToolHandler.TOOL_NAME)
                .description("按配置检索 SKU 样本；每条命中含 https detailUrl")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(SearchSkuToolHandler.TOOL_NAME)
                .description("检索 SKU，返回带 https detailUrl 的 hits")
                .text("[search_sku] 按配置检索 SKU 样本。使用 query，可选 pageSize；禁止编造 detailUrl。")
                .schema(schema)
                .handlerClass(SearchSkuToolHandler.class.getName())
                .build();
        return new Tool(definition, new SearchSkuToolHandler(skuSearcher));
    }

    static Tool searchXhsNoteTool(XhsNoteSearcher xhsNoteSearcher) {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "搜索关键词，例如 厨房收纳");
        ObjectNode pageSize = properties.putObject("pageSize");
        pageSize.put("type", "integer");
        pageSize.put("description", "每页条数，默认 10，最大 20");
        parameters.putArray("required").add("query");
        ToolSchema schema = ToolSchema.builder()
                .name(SearchXhsNoteToolHandler.TOOL_NAME)
                .description("按配置检索小红书笔记样本；每条命中含 https noteUrl")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(SearchXhsNoteToolHandler.TOOL_NAME)
                .description("检索小红书笔记，返回带 https noteUrl 的 hits")
                .text("[search_xhs_note] 按配置检索小红书笔记样本。使用 query，可选 pageSize；禁止编造 noteUrl。")
                .schema(schema)
                .handlerClass(SearchXhsNoteToolHandler.class.getName())
                .build();
        return new Tool(definition, new SearchXhsNoteToolHandler(xhsNoteSearcher));
    }

    static Tool fetchXhsNoteTool(XhsNoteFetchPort xhsNoteFetchPort) {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode url = properties.putObject("url");
        url.put("type", "string");
        url.put("description", "小红书笔记 URL 或分享短链");
        ObjectNode noteUrl = properties.putObject("noteUrl");
        noteUrl.put("type", "string");
        noteUrl.put("description", "url 的别名");
        parameters.putArray("required").add("url");
        ToolSchema schema = ToolSchema.builder()
                .name(FetchXhsNoteToolHandler.TOOL_NAME)
                .description("按 URL 拉取一篇小红书笔记正文；失败不编造原文")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(FetchXhsNoteToolHandler.TOOL_NAME)
                .description("拉取小红书笔记详情，返回 title/body/noteUrl")
                .text("[fetch_xhs_note] 按 url（或 noteUrl）拉取一篇笔记正文。禁止编造正文。")
                .schema(schema)
                .handlerClass(FetchXhsNoteToolHandler.class.getName())
                .build();
        return new Tool(definition, new FetchXhsNoteToolHandler(xhsNoteFetchPort));
    }
}
