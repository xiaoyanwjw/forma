package com.xmut.lims.pi.agent.config;

import com.xmut.lims.pi.agent.tool.AskHumanToolHandler;
import com.xmut.lims.pi.agent.tool.workspace.BashToolHandler;
import com.xmut.lims.pi.agent.tool.workspace.ReadFileToolHandler;
import com.xmut.lims.pi.agent.tool.workspace.WriteFileToolHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Pi built-in Handler beans. Schema comes from {@code tools/base/*.tool.json}.
 */
@Configuration
public class BaseToolsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AskHumanToolHandler askHumanToolHandler() {
        return new AskHumanToolHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public WriteFileToolHandler writeFileToolHandler() {
        return new WriteFileToolHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadFileToolHandler readFileToolHandler() {
        return new ReadFileToolHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public BashToolHandler bashToolHandler() {
        return new BashToolHandler();
    }
}
