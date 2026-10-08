package com.xmut.forma.extension.tool.product.recall.source.ph.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyProductHuntHitMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void maps_cazadores_and_legacy_shapes_to_candidates() throws Exception {
        ArrayNode items = (ArrayNode) MAPPER.readTree("["
                + "{\"name\":\"Clueso MCP\",\"productName\":\"Clueso\",\"tagline\":\"Create videos\","
                + "\"votes\":544,\"launchUrl\":\"https://www.producthunt.com/products/clueso/launches/x\","
                + "\"productUrl\":\"https://www.producthunt.com/products/clueso\","
                + "\"launchedAt\":\"2026-10-08T00:01:00-07:00\"},"
                + "{\"name\":\"Alpha\",\"tagline\":\"hi\",\"productUrl\":\"https://www.producthunt.com/posts/alpha\","
                + "\"votesCount\":12,\"launchDate\":\"2026-10-01T00:00:00Z\"},"
                + "{\"title\":\"Beta\",\"tagline\":\"yo\",\"url\":\"https://www.producthunt.com/posts/beta\"},"
                + "{\"name\":\"NoUrl\",\"tagline\":\"still ok\"},"
                + "{\"name\":\"\",\"productUrl\":\"https://www.producthunt.com/posts/empty\"}"
                + "]");

        List<ProductLaunchCandidate> mapped = ApifyProductHuntHitMapper.mapItems(items);

        assertEquals(4, mapped.size());
        assertEquals("Clueso MCP", mapped.get(0).getTitle());
        assertEquals("Create videos", mapped.get(0).getTagline());
        assertEquals("https://www.producthunt.com/products/clueso/launches/x", mapped.get(0).getUrl());
        assertEquals(Integer.valueOf(544), mapped.get(0).getVotes());
        assertEquals("2026-10-08T00:01:00-07:00", mapped.get(0).getPublishedAt());
        assertEquals("ph", mapped.get(0).getSource());
        assertEquals("Alpha", mapped.get(1).getTitle());
        assertEquals("https://www.producthunt.com/posts/alpha", mapped.get(1).getUrl());
        assertEquals("Beta", mapped.get(2).getTitle());
        assertEquals("NoUrl", mapped.get(3).getTitle());
        assertTrue(mapped.get(3).getUrl() == null || mapped.get(3).getUrl().isEmpty());
    }
}
