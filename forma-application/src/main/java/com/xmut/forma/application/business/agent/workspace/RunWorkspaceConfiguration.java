package com.xmut.forma.application.business.agent.workspace;

import com.xmut.forma.common.workspace.RunWorkspacePaths;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RunWorkspaceProperties.class)
public class RunWorkspaceConfiguration {

    public RunWorkspaceConfiguration(RunWorkspaceProperties properties) {
        RunWorkspacePaths.bindRoot(properties.getRoot());
    }
}
