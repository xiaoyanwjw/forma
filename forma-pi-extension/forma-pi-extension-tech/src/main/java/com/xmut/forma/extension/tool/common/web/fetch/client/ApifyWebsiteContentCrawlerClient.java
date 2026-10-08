package com.xmut.forma.extension.tool.common.web.fetch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.common.ApifyActorTransport;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.web.fetch.WebFetchUrls;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchProperties;
import org.springframework.util.StringUtils;

/**
 * Apify {@code apify/website-content-crawler} locked to a single page ({@code maxCrawlDepth}=0).
 * Implements 抓取 {@link PageFetchPort} ({@code maxCrawlPages}=1).
 */
public final class ApifyWebsiteContentCrawlerClient implements PageFetchPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebFetchProperties properties;
    private final ApifyActorTransport transport;

    public ApifyWebsiteContentCrawlerClient(WebFetchProperties properties, ApifyActorTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public WebFetchHit fetch(String url) {
        WebFetchUrls.validatePublicHttpUrl(url);
        WebFetchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }
        String body = requestBody(url.trim());
        String response = transport.post(apify.getActorId(), token.trim(), apify.getTimeoutMs(), body);
        return resolve(response);
    }

    private static String requestBody(String url) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode startUrls = root.putArray("startUrls");
        startUrls.addObject().put("url", url);
        root.put("maxCrawlDepth", 0);
        root.put("maxCrawlPages", 1);
        root.put("maxResults", 1);
        root.put("crawlerType", "playwright:firefox");
        root.putObject("proxyConfiguration").put("useApifyProxy", true);
        root.put("saveMarkdown", true);
        root.put("useSitemaps", false);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("apify_error: " + e.getMessage(), e);
        }
    }

    private static WebFetchHit resolve(String responseBody) {
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
            return ApifyWccHitMapper.mapFirst((ArrayNode) root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("apify_parse_error: " + e.getMessage(), e);
        }
    }
}
