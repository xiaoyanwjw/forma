package com.xmut.forma.extension.tool.sku.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.sku.port.SkuSearchHit;
import com.xmut.forma.extension.tool.sku.port.SkuSearchPort;
import com.xmut.forma.extension.tool.sku.port.SkuSearchProperties;
import com.xmut.forma.extension.common.ApifyActorTransport;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

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

    @SneakyThrows
    @Override
    public List<SkuSearchHit> search(String query, String platform, int pageSize) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        int maxItems = pageSize < 1 ? 1 : Math.min(pageSize, 20);
        SkuSearchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.put("keyword", query);
        root.put("maxItems", maxItems);
        root.put("enrichWithDetails", false);
        root.put("fetchReviews", false);
        String body= MAPPER.writeValueAsString(root);

        String response = transport.post(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return resolve(response);
    }

    private static List<SkuSearchHit> resolve(String responseBody) {
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
