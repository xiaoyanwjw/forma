package com.xmut.ebus.extension.tool.xhs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.extension.common.ApifyActorTransport;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchHit;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchPort;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchProperties;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

/**
 * Apify {@code opspilot.cc/xiaohongshu-keyword-search-scraper} implementation of {@link XhsNoteSearchPort}.
 */
public final class ApifyXhsNoteSearchClient implements XhsNoteSearchPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final XhsNoteSearchProperties properties;
    private final ApifyActorTransport transport;

    public ApifyXhsNoteSearchClient(XhsNoteSearchProperties properties, ApifyActorTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @SneakyThrows
    @Override
    public List<XhsNoteSearchHit> search(String query, int pageSize) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        XhsNoteSearchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("keyword", query.trim());
        root.put("page", 1);
        String body = MAPPER.writeValueAsString(root);
        String response = transport.post(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return resolve(response);
    }

    private static List<XhsNoteSearchHit> resolve(String responseBody) {
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
            return ApifyXhsNoteHitMapper.mapItems((ArrayNode) root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("apify_parse_error: " + e.getMessage(), e);
        }
    }
}
