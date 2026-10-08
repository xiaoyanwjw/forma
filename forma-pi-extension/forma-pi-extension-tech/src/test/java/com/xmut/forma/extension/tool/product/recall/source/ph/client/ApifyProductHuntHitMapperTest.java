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
    void maps_cloud9_shape_to_candidates() throws Exception {
        ArrayNode items = (ArrayNode) MAPPER.readTree("["
                + "{\"name\":\"Alpha\",\"tagline\":\"hi\",\"productUrl\":\"https://www.producthunt.com/posts/alpha\","
                + "\"votesCount\":12,\"launchDate\":\"2026-10-01T00:00:00Z\"},"
                + "{\"title\":\"Beta\",\"tagline\":\"yo\",\"url\":\"https://www.producthunt.com/posts/beta\"},"
                + "{\"name\":\"NoUrl\",\"tagline\":\"still ok\"},"
                + "{\"name\":\"\",\"productUrl\":\"https://www.producthunt.com/posts/empty\"}"
                + "]");

        List<ProductLaunchCandidate> mapped = ApifyProductHuntHitMapper.mapItems(items);

        assertEquals(3, mapped.size());
        assertEquals("Alpha", mapped.get(0).getTitle());
        assertEquals("hi", mapped.get(0).getTagline());
        assertEquals("https://www.producthunt.com/posts/alpha", mapped.get(0).getUrl());
        assertEquals(Integer.valueOf(12), mapped.get(0).getVotes());
        assertEquals("2026-10-01T00:00:00Z", mapped.get(0).getPublishedAt());
        assertEquals("ph", mapped.get(0).getSource());
        assertEquals("Beta", mapped.get(1).getTitle());
        assertEquals("NoUrl", mapped.get(2).getTitle());
        assertTrue(mapped.get(2).getUrl() == null || mapped.get(2).getUrl().isEmpty());
    }
}
