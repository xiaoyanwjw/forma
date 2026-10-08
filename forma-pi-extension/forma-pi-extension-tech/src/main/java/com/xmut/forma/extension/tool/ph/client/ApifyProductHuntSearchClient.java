package com.xmut.forma.extension.tool.ph.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.common.ApifyActorTransport;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchCandidate;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchProperties;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

/**
 * Apify Product Hunt list Actor → {@link ProductLaunchCandidate}.
 * Default actor: {@link ProductLaunchSearchProperties#DEFAULT_ACTOR_ID}.
 */
public final class ApifyProductHuntSearchClient implements ProductLaunchSearchPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProductLaunchSearchProperties properties;
    private final ApifyActorTransport transport;

    public ApifyProductHuntSearchClient(ProductLaunchSearchProperties properties, ApifyActorTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @SneakyThrows
    @Override
    public List<ProductLaunchCandidate> search(String topic, int fetchLimit) {
        if (!StringUtils.hasText(topic)) {
            return Collections.emptyList();
        }
        ProductLaunchSearchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }
        int maxResults = fetchLimit < 1 ? 1 : Math.min(fetchLimit, 100);
        String body = requestBody(topic.trim(), maxResults, apify.getProductHuntToken());
        String response = transport.post(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return resolve(response);
    }

    private static String requestBody(String topic, int maxResults, String productHuntToken) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        // cloud9_ai/producthunt-scraper input
        root.put("searchQuery", topic);
        root.put("timeFrame", "today");
        root.put("sortBy", "popular");
        root.put("maxResults", maxResults);
        // tolerant aliases for other PH actors
        root.put("query", topic);
        root.put("keyword", topic);
        root.put("maxItems", maxResults);
        if (StringUtils.hasText(productHuntToken)) {
            root.put("apiToken", productHuntToken.trim());
            root.put("productHuntToken", productHuntToken.trim());
        }
        return MAPPER.writeValueAsString(root);
    }

    private static List<ProductLaunchCandidate> resolve(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = MAPPER.readTree(responseBody.trim());
            if (root.isObject() && root.has("error")) {
                JsonNode error = root.get("error");
                String message = error != null && error.isObject() && error.has("message")
                        ? error.get("message").asText()
                        : root.toString();
                throw new IllegalStateException("apify_error: " + message);
            }
            if (!root.isArray()) {
                throw new IllegalStateException("apify_error: expected dataset array");
            }
            return ApifyProductHuntHitMapper.mapItems((ArrayNode) root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("apify_parse_error: " + e.getMessage(), e);
        }
    }
}
