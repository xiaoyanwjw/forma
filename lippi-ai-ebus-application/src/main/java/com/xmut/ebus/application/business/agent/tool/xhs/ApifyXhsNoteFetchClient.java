package com.xmut.ebus.application.business.agent.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyActorTransport;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Apify {@code khadinakbar/xiaohongshu-note-detail-scraper} implementation of {@link XhsNoteFetchPort}.
 */
public final class ApifyXhsNoteFetchClient implements XhsNoteFetchPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final XhsNoteFetchProperties properties;
    private final ApifyActorTransport transport;

    public ApifyXhsNoteFetchClient(XhsNoteFetchProperties properties, ApifyActorTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @SneakyThrows
    @Override
    public XhsNoteFetchHit fetch(String noteRef) {
        if (!StringUtils.hasText(noteRef)) {
            throw new IllegalStateException("note_ref_required");
        }
        XhsNoteFetchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode urls = root.putArray("noteUrls");
        urls.add(noteRef.trim());
        String body = MAPPER.writeValueAsString(root);
        String response = transport.post(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return resolve(response);
    }

    private static XhsNoteFetchHit resolve(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("apify_error: empty dataset");
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
            Optional<XhsNoteFetchHit> hit = ApifyXhsNoteFetchMapper.mapFirst((ArrayNode) root);
            if (!hit.isPresent()) {
                throw new IllegalStateException("apify_error: empty dataset");
            }
            return hit.get();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("apify_parse_error: " + e.getMessage(), e);
        }
    }
}
