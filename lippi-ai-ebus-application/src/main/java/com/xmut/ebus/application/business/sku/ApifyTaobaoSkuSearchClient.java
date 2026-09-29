package com.xmut.ebus.application.business.sku;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Apify {@code zen-studio/taobao-search-scraper} implementation of {@link SkuSearchPort}.
 */
public final class ApifyTaobaoSkuSearchClient implements SkuSearchPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkuSearchProperties properties;
    private final ApifyActorTransport transport;

    public ApifyTaobaoSkuSearchClient(SkuSearchProperties properties, ApifyActorTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public List<SkuSearchHit> search(String query, String platform, int pageSize) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String keyword = query.trim();
        int maxItems = pageSize < 1 ? 1 : Math.min(pageSize, 20);
        SkuSearchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }
        String body = buildRequestBody(keyword, maxItems);
        String response = transport.postSyncDatasetItems(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return parseDatasetItems(response);
    }

    private static String buildRequestBody(String keyword, int maxItems) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("keyword", keyword);
            root.put("maxItems", maxItems);
            root.put("enrichWithDetails", false);
            root.put("fetchReviews", false);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("request_body_error", e);
        }
    }

    private static List<SkuSearchHit> parseDatasetItems(String responseBody) {
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
            return ApifyTaobaoHitMapper.mapItems((ArrayNode) root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("apify_parse_error: " + e.getMessage(), e);
        }
    }
}
