package com.xmut.ebus.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.agent.tool.AskHumanToolHandler;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyOkHttpTransport;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyTaobaoSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.FallbackSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.MockSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchPort;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
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
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;

/**
 * Primary {@link ToolCatalog} for Adam: {@code read_skill} + {@code search_sku} + {@code ask_human}.
 *
 * <p>Not {@code @ConditionalOnMissingBean} — this bean must replace pi-agent's default
 * catalog so {@code search_sku} is registered at startup.
 */
@Configuration
@EnableConfigurationProperties(SkuSearchProperties.class)
public class EbusPiToolCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EbusPiToolCatalogConfiguration.class);

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

    @Primary
    @Bean
    public ToolCatalog toolCatalog(SkillCatalog skillCatalog, SkuSearchPort skuSearchPort) {
        return InMemoryToolCatalog.of(Arrays.asList(
                readSkillTool(skillCatalog),
                searchSkuTool(skuSearchPort),
                askHumanTool()));
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

    static Tool searchSkuTool(SkuSearchPort port) {
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
        return new Tool(definition, new SearchSkuToolHandler(port));
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
}
