package com.xmut.lims.pi.agent.resource;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冻结的 Agent Context 视图。
 * 功能描述：包含 skills 目录、prompt 模板与已登记 extension 名。
 */
@Value
public class AgentResourceSnapshot {

    List<String> skillIds;
    List<String> toolIds;
    Map<String, PromptTemplate> prompts;
    List<String> extensionNames;

    public AgentResourceSnapshot(List<String> skillIds,
                                 List<String> toolIds,
                                 Map<String, PromptTemplate> prompts,
                                 List<String> extensionNames) {
        this.skillIds = skillIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(skillIds));
        this.toolIds = toolIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolIds));
        this.prompts = prompts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(prompts));
        this.extensionNames = extensionNames == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(extensionNames));
    }
}
