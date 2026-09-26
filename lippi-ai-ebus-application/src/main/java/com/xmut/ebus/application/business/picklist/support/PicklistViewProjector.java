package com.xmut.ebus.application.business.picklist.support;

import com.xmut.ebus.application.business.computer.ComputerBlock;
import com.xmut.ebus.application.business.computer.ComputerDocument;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.common.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Projects persisted picklist artifact into a {@link ComputerDocument} map for SSE view.
 */
@Component
public class PicklistViewProjector {

    private static final String TITLE = "选品清单";
    private static final String STATUS = "已结算";
    private static final String PRIORITY_BADGE = "优先试";

    public Map<String, Object> project(PicklistArtifactDTO dto) {
        List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
        if (dto == null) {
            return new ComputerDocument(1, TITLE, STATUS, blocks).toMap();
        }
        if (StringUtils.hasText(dto.getDisclaimer())) {
            blocks.add(ComputerBlock.note(dto.getDisclaimer(), "mute"));
        }
        if (StringUtils.hasText(dto.getAssumptions())) {
            blocks.add(ComputerBlock.note("假设：" + dto.getAssumptions(), null));
        }
        blocks.add(ComputerBlock.list(true, projectItems(dto.getItems())));
        return new ComputerDocument(1, TITLE, STATUS, blocks).toMap();
    }

    private static List<Map<String, Object>> projectItems(List<PicklistArtifactDTO.PicklistItemDTO> items) {
        List<Map<String, Object>> listItems = new ArrayList<Map<String, Object>>();
        if (items == null) {
            return listItems;
        }
        for (PicklistArtifactDTO.PicklistItemDTO item : items) {
            listItems.add(projectItem(item));
        }
        return listItems;
    }

    private static Map<String, Object> projectItem(PicklistArtifactDTO.PicklistItemDTO item) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        String rawTitle = item.getTitle() == null ? "" : item.getTitle();
        if (rawTitle.startsWith(PicklistArtifactParser.PRIORITY_MARK)) {
            row.put("badge", PRIORITY_BADGE);
            row.put("title", rawTitle.substring(PicklistArtifactParser.PRIORITY_MARK.length()));
        } else {
            row.put("title", rawTitle);
        }
        List<String> lines = new ArrayList<String>();
        if (StringUtils.hasText(item.getPriceBand())) {
            lines.add("价格带：" + item.getPriceBand());
        }
        if (StringUtils.hasText(item.getReason())) {
            lines.add(item.getReason());
        }
        if (StringUtils.hasText(item.getDifferentiation())) {
            lines.add(item.getDifferentiation());
        }
        if (!lines.isEmpty()) {
            row.put("lines", lines);
        }
        List<String> tags = new ArrayList<String>();
        appendTag(tags, "需求 ", item.getDemand());
        appendTag(tags, "竞争 ", item.getCompetition());
        appendTag(tags, "利润 ", item.getMargin());
        appendTag(tags, "风险 ", item.getRisk());
        if (!tags.isEmpty()) {
            row.put("tags", tags);
        }
        return row;
    }

    private static void appendTag(List<String> tags, String prefix, String value) {
        if (StringUtils.hasText(value)) {
            tags.add(prefix + value);
        }
    }
}
