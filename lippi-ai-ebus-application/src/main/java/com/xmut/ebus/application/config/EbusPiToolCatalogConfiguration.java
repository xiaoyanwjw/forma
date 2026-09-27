package com.xmut.ebus.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.sku.MockSkuSearchClient;
import com.xmut.ebus.application.business.sku.SearchSkuToolHandler;
import com.xmut.ebus.application.business.sku.SkuSearchPort;
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
 * Primary {@link ToolCatalog} for Adam: {@code read_skill} + {@code search_sku}.
 *
 * <p>Not {@code @ConditionalOnMissingBean} — this bean must replace pi-agent's default
 * catalog so {@code search_sku} is registered at startup.
 */
@Configuration
public class EbusPiToolCatalogConfiguration {

    @Bean
    public SkuSearchPort skuSearchPort() {
        return new MockSkuSearchClient();
    }

    @Primary
    @Bean
    public ToolCatalog toolCatalog(SkillCatalog skillCatalog, SkuSearchPort skuSearchPort) {
        return InMemoryToolCatalog.of(Arrays.asList(
                readSkillTool(skillCatalog),
                searchSkuTool(skuSearchPort)));
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
                .description("Search promoted SKU samples; every hit includes an https detailUrl")
                .parametersSchema(parameters)
                .build();
        ToolDefinition definition = ToolDefinition.builder()
                .id(SearchSkuToolHandler.TOOL_NAME)
                .description("Search SKUs and return hits with https detailUrl")
                .text("[search_sku] Search promoted SKUs. Use query; never invent detailUrl.")
                .schema(schema)
                .handlerClass(SearchSkuToolHandler.class.getName())
                .build();
        return new Tool(definition, new SearchSkuToolHandler(port));
    }
}
