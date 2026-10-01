package com.xmut.ebus.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.agent.tool.AskHumanToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.BashToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.ReadFileToolHandler;
import com.xmut.ebus.application.business.agent.tool.workspace.WriteFileToolHandler;
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
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ModelXhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearcher;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.Tool;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.OverlayModelCatalog;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Primary {@link ToolCatalog} for Adam: {@code read_skill} + {@code search_sku} + {@code search_xhs_note}
 * + {@code ask_human} + workspace {@code write_file} / {@code read_file} / {@code bash}.
 *
 * <p>Not {@code @ConditionalOnMissingBean} — this bean must replace pi-agent's default
 * catalog so {@code search_sku} is registered at startup.
 */
@Configuration
@EnableConfigurationProperties({SkuSearchProperties.class, XhsNoteSearchProperties.class})
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
        MockXhsNoteSearchClient mock = new MockXhsNoteSearchClient();
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return mock;
        }
        String actorId = props.getApify().getActorId();
        String token = props.getApify().getToken();
        if (token == null || token.trim().isEmpty()) {
            LoggerUtils.error(
                    log,
                    PiToolCatalogConfiguration.class,
                    "xhsNoteSearchPort",
                    "missing_token",
                    NameValue.create("client", "apify"),
                    NameValue.create("actorId", actorId),
                    NameValue.create("queryLen", 0),
                    NameValue.create("hitCount", 0));
            return mock;
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

    @Primary
    @Bean
    public ToolCatalog toolCatalog(SkillCatalog skillCatalog, SkuSearcher skuSearcher, XhsNoteSearcher xhsNoteSearcher) {
        return InMemoryToolCatalog.of(Arrays.asList(
                readSkillTool(skillCatalog),
                searchSkuTool(skuSearcher),
                searchXhsNoteTool(xhsNoteSearcher),
                askHumanTool(),
                writeFileTool(),
                readFileTool(),
                bashTool()));
    }

    /**
     * Copied from {@code AgentConfiguration.readSkillTool} (package-visible in pi-agent).
     */
    static Tool readSkillTool(SkillCatalog skillCatalog) {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode skillId = properties.putObject("skill_id");
        skillId.put("type", "string");
        skillId.put("description", "已注册的技能 id，例如 ecommerce-picklist");
        parameters.putArray("required").add("skill_id");
        ToolSchema schema = ToolSchema.builder()
                .name(ReadSkill.TOOL_ID)
                .description("按 skill_id 从技能目录读取完整技能正文")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(ReadSkill.TOOL_ID)
                .description("按 skill_id 加载已注册技能的完整 Markdown 正文")
                .text("[read_skill] 按 skill_id 加载技能正文。禁止编造技能内容。")
                .schema(schema)
                .handlerClass(ReadSkill.class.getName())
                .build();
        return new Tool(definition, new ReadSkill(skillCatalog));
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

    static Tool askHumanTool() {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode question = properties.putObject("question");
        question.put("type", "string");
        question.put("description", "展示给用户的问题");
        ObjectNode options = properties.putObject("options");
        options.put("type", "array");
        options.put("description", "可选项，含 id 与 label");
        ObjectNode optionItems = options.putObject("items");
        optionItems.put("type", "object");
        ObjectNode optionProps = optionItems.putObject("properties");
        optionProps.putObject("id").put("type", "string");
        optionProps.putObject("label").put("type", "string");
        ObjectNode allowFreeText = properties.putObject("allowFreeText");
        allowFreeText.put("type", "boolean");
        allowFreeText.put("description", "是否允许自由文本作答；默认 true");
        parameters.putArray("required").add("question").add("options");
        ToolSchema schema = ToolSchema.builder()
                .name(AskHumanToolHandler.TOOL_NAME)
                .description("向用户发起带选项的结构化提问；调用后挂起直至 resume")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(AskHumanToolHandler.TOOL_NAME)
                .description("请用户确认或补充信息")
                .text("[ask_human] 向用户提问并给出选项。禁止编造用户答复。")
                .schema(schema)
                .handlerClass(AskHumanToolHandler.class.getName())
                .build();
        return new Tool(definition, new AskHumanToolHandler());
    }

    static Tool writeFileTool() {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "相对 run 工作区的路径");
        ObjectNode content = properties.putObject("content");
        content.put("type", "string");
        content.put("description", "要写入的 UTF-8 文本");
        parameters.putArray("required").add("path").add("content");
        ToolSchema schema = ToolSchema.builder()
                .name(WriteFileToolHandler.TOOL_NAME)
                .description("在 run 工作区写入 UTF-8 文本文件")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(WriteFileToolHandler.TOOL_NAME)
                .description("在 run 工作区写入文件")
                .text("[write_file] 将 UTF-8 文本写入相对路径。路径不得逃出工作区。")
                .schema(schema)
                .handlerClass(WriteFileToolHandler.class.getName())
                .build();
        return new Tool(definition, new WriteFileToolHandler());
    }

    static Tool readFileTool() {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "相对 run 工作区的路径");
        parameters.putArray("required").add("path");
        ToolSchema schema = ToolSchema.builder()
                .name(ReadFileToolHandler.TOOL_NAME)
                .description("在 run 工作区读取 UTF-8 文本文件")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(ReadFileToolHandler.TOOL_NAME)
                .description("在 run 工作区读取文件")
                .text("[read_file] 按相对路径读取 UTF-8 文本。超过 2MiB 的文件会失败。")
                .schema(schema)
                .handlerClass(ReadFileToolHandler.class.getName())
                .build();
        return new Tool(definition, new ReadFileToolHandler());
    }

    static Tool bashTool() {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "Shell 命令；cwd 为 run 工作区。进程仍在宿主机文件系统上执行。");
        parameters.putArray("required").add("command");
        ToolSchema schema = ToolSchema.builder()
                .name(BashToolHandler.TOOL_NAME)
                .description("在 run 工作区执行 bash 命令。命令仍在宿主机文件系统上运行。")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(BashToolHandler.TOOL_NAME)
                .description("在 run 工作区执行 bash。命令仍在宿主机文件系统上运行。")
                .text("[bash] 在 run 工作区执行命令（cwd = 工作区）。进程仍跑在宿主机文件系统上；环境变量仅 PATH、LANG、HOME。输出截断至 64KiB。")
                .schema(schema)
                .handlerClass(BashToolHandler.class.getName())
                .build();
        return new Tool(definition, new BashToolHandler());
    }
}
