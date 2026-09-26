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
    void parsesFencedJsonWithEightItems() {
        PersistPicklistCommand cmd = parser.parse(sampleJson(8), "u1", "r1");
        assertEquals("domestic-generic-default", cmd.getTemplateId());
        assertTrue(cmd.getDisclaimer().contains("非实时"));
        assertEquals(8, cmd.getItems().size());
        assertEquals("品0", cmd.getItems().get(0).getTitle());
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
                + "\"items\":[" + itemJson(0) + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsWrongTemplateId() {
        String json = "{"
                + "\"templateId\":\"other-template\","
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + itemJson(0) + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void normalizesMissingTemplateIdToDefault() {
        String json = "{"
                + "\"disclaimer\":\"基于通用知识推断，非实时平台数据\","
                + "\"items\":[" + itemJson(0) + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        PersistPicklistCommand cmd = parser.parse(json, "u1", "r1");
        assertEquals("domestic-generic-default", cmd.getTemplateId());
    }

    @Test
    void rejectsMissingDisclaimer() {
        String json = "{"
                + "\"templateId\":\"domestic-generic-default\","
                + "\"items\":[" + itemJson(0) + "," + itemJson(1) + "," + itemJson(2) + ","
                + itemJson(3) + "," + itemJson(4) + "," + itemJson(5) + ","
                + itemJson(6) + "," + itemJson(7) + "]"
                + "}";
        assertThrows(BusinessException.class, () -> parser.parse(json, "u1", "r1"));
    }

    @Test
    void rejectsMissingReasonOnItem() {
        String brokenItem = "{"
                + "\"title\":\"x\",\"priceBand\":\"1\",\"differentiation\":\"d\","
                + "\"demand\":\"a\",\"competition\":\"b\",\"margin\":\"c\",\"risk\":\"e\""
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

    private static String sampleJson(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("```json\n{");
        sb.append("\"templateId\":\"domestic-generic-default\",");
        sb.append("\"disclaimer\":\"基于通用电商知识推断，非实时平台数据\",");
        sb.append("\"assumptions\":\"默认\",");
        sb.append("\"items\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(itemJson(i));
        }
        sb.append("]}\n```");
        return sb.toString();
    }

    private static String itemJson(int i) {
        return "{"
                + "\"title\":\"品" + i + "\","
                + "\"priceBand\":\"19-39\","
                + "\"reason\":\"理由" + i + "\","
                + "\"differentiation\":\"差异" + i + "\","
                + "\"demand\":\"需求\","
                + "\"competition\":\"竞争\","
                + "\"margin\":\"利润\","
                + "\"risk\":\"风险\""
                + "}";
    }
}
