package com.xmut.forma.extension.tool.common.web.fetch.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyWccHitMapperTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void maps_fixture_first_item() throws Exception {
        ArrayNode items = readFixture();
        WebFetchHit hit = ApifyWccHitMapper.mapFirst(items);
        assertEquals("https://example.com/product", hit.getFinalUrl());
        assertEquals("示例产品", hit.getTitle());
        assertTrue(hit.getText().startsWith("Forma Edge 推理节点可在 Docker 中启动"));
        assertTrue(hit.getText().contains("OpenAI 兼容 HTTP API"));
        assertFalse(hit.getText().startsWith("#"));
        assertFalse(hit.isTruncated());
        assertTrue(hit.getText().replaceAll("\\s", "").length() >= 400);
    }

    @Test
    void prefers_text_over_markdown_and_falls_back_when_text_blank() {
        ArrayNode prefer = om.createArrayNode();
        ObjectNode row = prefer.addObject();
        row.put("url", "https://example.com/a");
        row.put("text", longBody("正文优先"));
        row.put("markdown", longBody("markdown不应入选"));
        row.putObject("metadata").put("title", "有标题");
        WebFetchHit hit = ApifyWccHitMapper.mapFirst(prefer);
        assertTrue(hit.getText().startsWith("正文优先"));
        assertEquals("有标题", hit.getTitle());

        ArrayNode fallback = om.createArrayNode();
        ObjectNode md = fallback.addObject();
        md.put("url", "https://example.com/b");
        md.put("text", "  ");
        md.put("markdown", longBody("改用markdown"));
        WebFetchHit fromMd = ApifyWccHitMapper.mapFirst(fallback);
        assertTrue(fromMd.getText().startsWith("改用markdown"));
        assertEquals("untitled", fromMd.getTitle());
    }

    @Test
    void truncates_at_32000_and_rejects_short_body() {
        ArrayNode huge = om.createArrayNode();
        ObjectNode row = huge.addObject();
        row.put("url", "https://example.com/long");
        row.put("text", repeat('测', 32050));
        row.putObject("metadata").put("title", "长文");
        WebFetchHit hit = ApifyWccHitMapper.mapFirst(huge);
        assertEquals(32000, hit.getText().length());
        assertTrue(hit.isTruncated());
        assertEquals("长文", hit.getTitle());

        ArrayNode shortItems = om.createArrayNode();
        ObjectNode shortRow = shortItems.addObject();
        shortRow.put("url", "https://example.com/short");
        shortRow.put("text", "登录墙");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ApifyWccHitMapper.mapFirst(shortItems));
        assertEquals("empty_body", ex.getMessage());
    }

    private ArrayNode readFixture() throws Exception {
        InputStream in = ApifyWccHitMapperTest.class.getResourceAsStream("/techdigest/fixtures/wcc-dataset.json");
        if (in == null) {
            throw new IllegalStateException("missing fixture");
        }
        try {
            return (ArrayNode) om.readTree(in);
        } finally {
            in.close();
        }
    }

    private static String longBody(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < 450) {
            sb.append("这是一段足够长的公开网页正文，用来通过四百字下限。");
        }
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
