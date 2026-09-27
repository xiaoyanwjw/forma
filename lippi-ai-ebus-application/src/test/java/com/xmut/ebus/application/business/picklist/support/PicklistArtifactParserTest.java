package com.xmut.ebus.application.business.picklist.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PicklistArtifactParserTest {

    private PicklistArtifactParser parser;

    @BeforeEach
    void setUp() {
        parser = new PicklistArtifactParser(new ObjectMapper());
    }

    @Test
    void parsesFencedJsonWithStructuredReasonFields() {
        PicklistParseResult result = parser.parse(sampleJson(8), "u1", "r1");
        PersistPicklistCommand cmd = result.getCommand();
        assertNull(result.getRawView());
        assertEquals("domestic-generic-default", cmd.getTemplateId());
        assertTrue(cmd.getDisclaimer().contains("非实时"));
        assertEquals(8, cmd.getItems().size());
        assertTrue(cmd.getItems().get(0).getTitle().startsWith(PicklistArtifactParser.PRIORITY_MARK));
        assertEquals("台面积水", cmd.getItems().get(0).getPainPoint());
        assertEquals("租房刚需", cmd.getItems().get(0).getAngle());
        assertEquals("多色套装", cmd.getItems().get(0).getDiff());
        assertEquals("细分0", cmd.getItems().get(0).getNiche());
        assertTrue(cmd.getItems().get(0).getDemand().startsWith("高"));
        assertEquals("https://item.example/0", cmd.getItems().get(0).getSourceUrl());
    }

    @Test
    void parsesDualTrackEnvelopeWithViewAndArtifact() {
        String json = "{"
                + "\"view\":{"
                + "\"version\":1,\"title\":\"report\",\"status\":\"ready\","
                + "\"blocks\":[{\"type\":\"note\",\"tone\":\"mute\",\"text\":\"免责声明\"}]"
                + "},"
                + "\"artifact\":{"
                + "\"templateId\":\"domestic-generic-default\","
                + "\"disclaimer\":\"基于通用电商知识推断，非实时平台数据\","
                + "\"assumptions\":\"默认\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}"
                + "}";
        PicklistParseResult result = parser.parse(json, "u1", "r1");
        assertEquals(8, result.getCommand().getItems().size());
        assertEquals("台面积水", result.getCommand().getItems().get(0).getPainPoint());
        Map<String, Object> view = result.getRawView();
        assertNotNull(view);
        assertEquals(1, ((Number) view.get("version")).intValue());
        assertEquals("report", view.get("title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals(1, blocks.size());
        assertEquals("note", blocks.get(0).get("type"));
    }

    @Test
    void parsesEnvelopeArtifactWithoutViewForLegacyFallback() {
        String json = "{"
                + "\"artifact\":{"
                + "\"disclaimer\":\"基于通用电商知识推断，非实时平台数据\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}"
                + "}";
        PicklistParseResult result = parser.parse(json, "u1", "r1");
        assertNull(result.getRawView());
        assertEquals(8, result.getCommand().getItems().size());
    }

    @Test
    void rejectsEightCompleteItemsMissingSourceUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(itemJson(i).replace(",\"sourceUrl\":\"https://item.example/" + i + "\"", ""));
        }
        sb.append("]}");
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(sb.toString(), "u1", "r1"));
        assertEquals(PicklistArtifactParser.MSG_UNUSABLE, ex.getMessage());
    }

    @Test
    void rejectsHttpSourceUrl() {
        String bad = itemJson(0).replace("https://item.example/0", "http://item.example/0");
        String json = "{"
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + bad + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
        assertEquals(PicklistArtifactParser.MSG_UNUSABLE, ex.getMessage());
    }

    @Test
    void rejectsTooFewItems() {
        assertThrows(BusinessException.class, () -> parser.parse(sampleJson(7), "u1", "r1"));
    }

    @Test
    void rejectsDisclaimerWithoutNonRealtimeMark() {
        String json = "{"
                + "\"templateId\":\"domestic-generic-default\","
                + "\"disclaimer\":\"仅供参考\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsWrongTemplateId() {
        String json = "{"
                + "\"templateId\":\"other-template\","
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void normalizesMissingTemplateIdToDefault() {
        String json = "{"
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}";
        PicklistParseResult result = parser.parse(json, "u1", "r1");
        assertEquals("domestic-generic-default", result.getCommand().getTemplateId());
    }

    @Test
    void rejectsMissingDisclaimer() {
        String json = "{"
                + "\"templateId\":\"domestic-generic-default\","
                + "\"items\":[" + itemsCsv(8) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsMissingPainPointOnItem() {
        String brokenItem = "{"
                + "\"title\":\"【优先试】x\",\"priceBand\":\"1\","
                + "\"angle\":\"a\",\"diff\":\"d\",\"niche\":\"厨房\","
                + "\"demand\":\"高｜a\",\"competition\":\"中｜b\",\"margin\":\"中｜c\",\"risk\":\"低｜e\""
                + "}";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[");
        sb.append(brokenItem);
        for (int i = 1; i < 8; i++) {
            sb.append(',').append(itemJson(i));
        }
        sb.append("]}");
        assertThrows(BusinessException.class, () -> parser.parse(sb.toString(), "u1", "r1"));
    }

    @Test
    void rejectsLegacyConcatenatedReasonWithoutStructuredFields() {
        String bad = "{"
                + "\"title\":\"【优先试】品0\","
                + "\"priceBand\":\"19-39\","
                + "\"reason\":\"痛点：台面；切入：刚需；差异：多色\","
                + "\"differentiation\":\"细分：细分0；差异动作\","
                + "\"demand\":\"高｜需求稳\","
                + "\"competition\":\"中｜可切\","
                + "\"margin\":\"中｜测款友好\","
                + "\"risk\":\"低｜注意表述\""
                + "}";
        String json = "{"
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + bad + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsMissingLevelPrefix() {
        String bad = itemJson(0).replace("高｜需求稳", "需求稳");
        String json = "{"
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + bad + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsZeroPriorityMarks() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(itemJson(i, false));
        }
        sb.append("]}");
        assertThrows(BusinessException.class, () -> parser.parse(sb.toString(), "u1", "r1"));
    }

    @Test
    void rejectsFewerThanThreeNiches() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(itemJson(i).replace("\"niche\":\"细分" + (i % 3) + "\"", "\"niche\":\"同一细分\""));
        }
        sb.append("]}");
        assertThrows(BusinessException.class, () -> parser.parse(sb.toString(), "u1", "r1"));
    }

    private static String sampleJson(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("```json\n{");
        sb.append("\"templateId\":\"domestic-generic-default\",");
        sb.append("\"disclaimer\":\"基于通用电商知识推断，非实时平台数据\",");
        sb.append("\"assumptions\":\"默认\",");
        sb.append("\"items\":[");
        sb.append(itemsCsv(count));
        sb.append("]}\n```");
        return sb.toString();
    }

    private static String itemsCsv(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(itemJson(i));
        }
        return sb.toString();
    }

    private static String itemJson(int i) {
        return itemJson(i, i == 0);
    }

    private static String itemJson(int i, boolean priority) {
        String title = (priority ? PicklistArtifactParser.PRIORITY_MARK : "") + "品" + i;
        int niche = i % 3;
        return "{"
                + "\"title\":\"" + title + "\","
                + "\"priceBand\":\"19-39\","
                + "\"painPoint\":\"台面积水\","
                + "\"angle\":\"租房刚需\","
                + "\"diff\":\"多色套装\","
                + "\"niche\":\"细分" + niche + "\","
                + "\"demand\":\"高｜需求稳\","
                + "\"competition\":\"中｜可切\","
                + "\"margin\":\"中｜测款友好\","
                + "\"risk\":\"低｜注意表述\","
                + "\"sourceUrl\":\"https://item.example/" + i + "\""
                + "}";
    }
}
