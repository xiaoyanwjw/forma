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
 * Emits semantic keys/kinds only — display copy (Chinese labels) belongs to the renderer.
 */
@Component
public class PicklistViewProjector {

    /** Document title key; FE maps to locale copy. */
    static final String TITLE_KEY = "picklist";
    /** Document status key; FE maps to locale copy. */
    static final String STATUS_KEY = "settled";
    /** List item badge key; FE maps to locale copy. */
    static final String BADGE_PRIORITY = "priority";

    public Map<String, Object> project(PicklistArtifactDTO dto) {
        List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
        if (dto == null) {
            return new ComputerDocument(1, TITLE_KEY, STATUS_KEY, blocks).toMap();
        }
        if (StringUtils.hasText(dto.getDisclaimer())) {
            blocks.add(ComputerBlock.note(dto.getDisclaimer(), "mute", null));
        }
        if (StringUtils.hasText(dto.getAssumptions())) {
            blocks.add(ComputerBlock.note(dto.getAssumptions(), null, "assumptions"));
        }
        blocks.add(ComputerBlock.list(true, projectItems(dto.getItems())));
        return new ComputerDocument(1, TITLE_KEY, STATUS_KEY, blocks).toMap();
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
            row.put("badge", BADGE_PRIORITY);
            row.put("title", rawTitle.substring(PicklistArtifactParser.PRIORITY_MARK.length()));
        } else {
            row.put("title", rawTitle);
        }
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        appendLine(lines, "priceBand", item.getPriceBand(), "price");
        appendLine(lines, "painPoint", item.getPainPoint(), null);
        appendLine(lines, "angle", item.getAngle(), null);
        appendLine(lines, "diff", item.getDiff(), null);
        appendLine(lines, "niche", item.getNiche(), null);
        if (!lines.isEmpty()) {
            row.put("lines", lines);
        }
        List<Map<String, Object>> tags = new ArrayList<Map<String, Object>>();
        appendDimTag(tags, "demand", item.getDemand(), DimKind.DEMAND);
        appendDimTag(tags, "competition", item.getCompetition(), DimKind.COMPETITION);
        appendDimTag(tags, "margin", item.getMargin(), DimKind.MARGIN);
        appendDimTag(tags, "risk", item.getRisk(), DimKind.RISK);
        if (!tags.isEmpty()) {
            row.put("tags", tags);
        }
        return row;
    }

    private static void appendLine(List<Map<String, Object>> lines, String kind, String text, String emphasis) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("kind", kind);
        line.put("text", text.trim());
        if (StringUtils.hasText(emphasis)) {
            line.put("emphasis", emphasis);
        }
        lines.add(line);
    }

    private enum DimKind {
        DEMAND,
        COMPETITION,
        MARGIN,
        RISK
    }

    private static void appendDimTag(List<Map<String, Object>> tags, String kind, String value, DimKind dimKind) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Map<String, Object> tag = new LinkedHashMap<String, Object>();
        tag.put("kind", kind);
        tag.put("text", value.trim());
        tag.put("tone", toneForDim(dimKind, value));
        tags.add(tag);
    }

    /**
     * Map picklist dim level token (高/中/低…) to closed Computer tag tones.
     */
    static String toneForDim(DimKind kind, String value) {
        String level = levelToken(value);
        boolean high = level.startsWith("高");
        boolean low = level.startsWith("低");
        switch (kind) {
            case DEMAND:
            case MARGIN:
                if (high) {
                    return "positive";
                }
                if (low) {
                    return "caution";
                }
                return "info";
            case COMPETITION:
                if (high) {
                    return "danger";
                }
                if (low) {
                    return "positive";
                }
                return "caution";
            case RISK:
                if (high) {
                    return "danger";
                }
                if (low) {
                    return "safe";
                }
                return "caution";
            default:
                return "neutral";
        }
    }

    private static String levelToken(String value) {
        String trimmed = value.trim();
        int cut = trimmed.indexOf('｜');
        if (cut < 0) {
            cut = trimmed.indexOf('|');
        }
        return cut >= 0 ? trimmed.substring(0, cut).trim() : trimmed;
    }
}
