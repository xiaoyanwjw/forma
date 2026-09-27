package com.xmut.ebus.application.business.picklist.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        PersistPicklistCommand cmd = parser.parse(sampleJson(8), "u1", "r1");
        assertEquals("domestic-generic-default", cmd.getTemplateId());
        assertTrue(cmd.getDisclaimer().contains("非实时"));
        assertEquals(8, cmd.getItems().size());
        assertTrue(cmd.getItems().get(0).getTitle().startsWith(PicklistArtifactParser.PRIORITY_MARK));
        assertEquals("台面积水", cmd.getItems().get(0).getPainPoint());
        assertEquals("租房刚需", cmd.getItems().get(0).getAngle());
        assertEquals("多色套装", cmd.getItems().get(0).getDiff());
        assertEquals("细分0", cmd.getItems().get(0).getNiche());
        assertTrue(cmd.getItems().get(0).getDemand().startsWith("高"));
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
        PersistPicklistCommand cmd = parser.parse(json, "u1", "r1");
        assertEquals("domestic-generic-default", cmd.getTemplateId());
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
                + "\"risk\":\"低｜注意表述\""
                + "}";
    }
}
