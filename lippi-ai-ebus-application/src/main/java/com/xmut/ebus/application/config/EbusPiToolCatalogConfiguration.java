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
 * Primary {@link ToolCatalog} for Adam: {@code read_skill} + {@code search_sku} + {@code ask_human}
 * + workspace {@code write_file} / {@code read_file} / {@code bash}.
 *
 * <p>Not {@code @ConditionalOnMissingBean} — this bean must replace pi-agent's default
 * catalog so {@code search_sku} is registered at startup.
 */
@Configuration
@EnableConfigurationProperties(SkuSearchProperties.class)
public class EbusPiToolCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EbusPiToolCatalogConfiguration.class);

    /**
     * Live {@link ModelCatalog} used by pi-ai routing. pi-agent / pi-ai only register
     * {@code pi.default}; SKU rerank needs {@code ebus.sku.rerank} on the same catalog.
     */
    @Primary
    @Bean
    public ModelCatalog ebusModelCatalog(SkuSearchProperties props) {
        return overlayWithSkuRerank(props);
    }

    static ModelCatalog overlayWithSkuRerank(SkuSearchProperties props) {
        OverlayModelCatalog overlay = new OverlayModelCatalog(InMemoryModelCatalog.defaults());
        String useCase = props != null ? props.getSearcher().getRerankUseCase() : null;
        if (!StringUtils.hasText(useCase)) {
            useCase = "ebus.sku.rerank";
        }
        overlay.putOverride(useCase, skuRerankDescriptor(useCase));
        return overlay;
    }

    static ModelDescriptor skuRerankDescriptor(String useCase) {
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
                    EbusPiToolCatalogConfiguration.class,
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

    @Primary
    @Bean
    public ToolCatalog toolCatalog(SkillCatalog skillCatalog, SkuSearcher skuSearcher) {
        return InMemoryToolCatalog.of(Arrays.asList(
                readSkillTool(skillCatalog),
                searchSkuTool(skuSearcher),
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
        skillId.put("description", "Registered skill id, e.g. ecommerce-picklist");
        parameters.putArray("required").add("skill_id");
        ToolSchema schema = ToolSchema.builder()
                .name(ReadSkill.TOOL_ID)
                .description("Read the full skill markdown/instructions for a skill_id from the Skills catalog")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(ReadSkill.TOOL_ID)
                .description("Load the full markdown body of a registered skill by skill_id")
                .text("[read_skill] Load skill body by skill_id. Never invent skill content.")
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
        query.put("description", "Search keyword, e.g. 香薰");
        ObjectNode platform = properties.putObject("platform");
        platform.put("type", "string");
        platform.put("description", "Marketplace id; default taobao_tbk");
        ObjectNode pageSize = properties.putObject("pageSize");
        pageSize.put("type", "integer");
        pageSize.put("description", "Page size, default 10, max 20");
        parameters.putArray("required").add("query");
        ToolSchema schema = ToolSchema.builder()
                .name(SearchSkuToolHandler.TOOL_NAME)
                .description("Search configured SKU samples; every hit includes an https detailUrl")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(SearchSkuToolHandler.TOOL_NAME)
                .description("Search SKUs and return hits with https detailUrl")
                .text("[search_sku] Search configured SKU samples. Use query; never invent detailUrl.")
                .schema(schema)
                .handlerClass(SearchSkuToolHandler.class.getName())
                .build();
        return new Tool(definition, new SearchSkuToolHandler(skuSearcher));
    }

    static Tool askHumanTool() {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode question = properties.putObject("question");
        question.put("type", "string");
        question.put("description", "Question shown to the human");
        ObjectNode options = properties.putObject("options");
        options.put("type", "array");
        options.put("description", "Selectable options with id and label");
        ObjectNode optionItems = options.putObject("items");
        optionItems.put("type", "object");
        ObjectNode optionProps = optionItems.putObject("properties");
        optionProps.putObject("id").put("type", "string");
        optionProps.putObject("label").put("type", "string");
        ObjectNode allowFreeText = properties.putObject("allowFreeText");
        allowFreeText.put("type", "boolean");
        allowFreeText.put("description", "Whether free-text answers are allowed; default true");
        parameters.putArray("required").add("question").add("options");
        ToolSchema schema = ToolSchema.builder()
                .name(AskHumanToolHandler.TOOL_NAME)
                .description("Ask the human a structured question with options; suspends until resume")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(AskHumanToolHandler.TOOL_NAME)
                .description("Ask the human to confirm or supplement")
                .text("[ask_human] Ask a question with options. Do not invent the human answer.")
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
        path.put("description", "Relative path under the run workspace");
        ObjectNode content = properties.putObject("content");
        content.put("type", "string");
        content.put("description", "UTF-8 text to write");
        parameters.putArray("required").add("path").add("content");
        ToolSchema schema = ToolSchema.builder()
                .name(WriteFileToolHandler.TOOL_NAME)
                .description("Write a UTF-8 text file under the run workspace")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(WriteFileToolHandler.TOOL_NAME)
                .description("Write a file under the run workspace")
                .text("[write_file] Write UTF-8 text to a relative path. Paths cannot escape the workspace.")
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
        path.put("description", "Relative path under the run workspace");
        parameters.putArray("required").add("path");
        ToolSchema schema = ToolSchema.builder()
                .name(ReadFileToolHandler.TOOL_NAME)
                .description("Read a UTF-8 text file under the run workspace")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(ReadFileToolHandler.TOOL_NAME)
                .description("Read a file under the run workspace")
                .text("[read_file] Read UTF-8 text from a relative path. Files larger than 2MiB fail.")
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
        command.put("description", "Shell command; cwd is the run workspace. Still runs on the host filesystem.");
        parameters.putArray("required").add("command");
        ToolSchema schema = ToolSchema.builder()
                .name(BashToolHandler.TOOL_NAME)
                .description("Run a bash command in the run workspace. The command still runs on the host filesystem.")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(BashToolHandler.TOOL_NAME)
                .description("Run bash in the run workspace. The command still runs on the host filesystem.")
                .text("[bash] Run a command with cwd = run workspace. The process still runs on the host filesystem; env is only PATH, LANG, and HOME. Output is truncated at 64KiB.")
                .schema(schema)
                .handlerClass(BashToolHandler.class.getName())
                .build();
        return new Tool(definition, new BashToolHandler());
    }
}
