package com.xmut.lims.pi.agent.config;

import com.xmut.lims.pi.agent.tool.base.AskHumanToolHandler;
import com.xmut.lims.pi.agent.tool.base.BashToolHandler;
import com.xmut.lims.pi.agent.tool.base.ReadFileToolHandler;
import com.xmut.lims.pi.agent.tool.base.WriteFileToolHandler;
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
