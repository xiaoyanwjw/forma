package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.service.PicklistApplicationService;
import com.xmut.ebus.application.business.picklist.support.PicklistArtifactParser;
import com.xmut.ebus.application.business.picklist.support.PicklistParseResult;
import com.xmut.ebus.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy picklist persist (Task 3/4 replace with {@link ArtifactPersistPlugin} + parser pipeline).
 */
@Component
public class PicklistArtifactPersistPlugin {

    private final PicklistArtifactParser picklistArtifactParser;
    private final PicklistApplicationService picklistApplicationService;

    public PicklistArtifactPersistPlugin(PicklistArtifactParser picklistArtifactParser,
                                         PicklistApplicationService picklistApplicationService) {
        this.picklistArtifactParser = picklistArtifactParser;
        this.picklistApplicationService = picklistApplicationService;
    }

    public PersistedGenerationArtifact persistFromFinalResponse(String userId,
                                                                String runId,
                                                                String sceneCode,
                                                                String finalResponse) {
        PicklistParseResult parsedResult = picklistArtifactParser.parse(finalResponse, userId, runId);
        PersistPicklistCommand parsed = parsedResult.getCommand();
        PersistPicklistCommand persistCommand = PersistPicklistCommand.builder()
                .userId(parsed.getUserId())
                .username(parsed.getUsername())
                .runId(parsed.getRunId())
                .sceneCode(sceneCode)
                .templateId(parsed.getTemplateId())
                .disclaimer(parsed.getDisclaimer())
                .assumptions(parsed.getAssumptions())
                .items(parsed.getItems())
                .build();
        PicklistArtifactDTO artifact = picklistApplicationService.persistUsable(persistCommand);
        return new PersistedGenerationArtifact(
                artifact.getPicklistId(),
                artifact,
                parsedResult.getRawView(),
                toReadyExtras(artifact));
    }

    private static Map<String, Object> toReadyExtras(PicklistArtifactDTO artifact) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("artifactType", "picklist");
        data.put("picklistId", artifact.getPicklistId());
        data.put("runId", artifact.getRunId());
        data.put("templateId", artifact.getTemplateId());
        data.put("disclaimer", artifact.getDisclaimer());
        if (StringUtils.hasText(artifact.getAssumptions())) {
            data.put("assumptions", artifact.getAssumptions());
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PicklistArtifactDTO.PicklistItemDTO item : artifact.getItems()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("title", item.getTitle());
            row.put("priceBand", item.getPriceBand());
            row.put("painPoint", item.getPainPoint());
            row.put("angle", item.getAngle());
            row.put("diff", item.getDiff());
            row.put("niche", item.getNiche());
            row.put("demand", item.getDemand());
            row.put("competition", item.getCompetition());
            row.put("margin", item.getMargin());
            row.put("risk", item.getRisk());
            row.put("sourceUrl", item.getSourceUrl());
            items.add(row);
        }
        data.put("items", items);
        return data;
    }
}
