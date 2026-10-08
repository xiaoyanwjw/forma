package com.xmut.forma.extension.tool.product.recall.source.ph.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.common.ApifyActorTransport;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchCandidate;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

/**
 * Apify Product Hunt list Actor → {@link ProductLaunchCandidate}.
 * Default actor: {@link ProductLaunchSearchProperties#DEFAULT_ACTOR_ID}
 * ({@code cazadores/product-hunt-scraper} daily leaderboard).
 */
public final class ApifyProductHuntSearchClient implements ProductLaunchSearchPort {

    /** Product Hunt calendar days use US Pacific midnight. */
    private static final ZoneId PH_ZONE = ZoneId.of("America/Los_Angeles");

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
        ProductLaunchSearchProperties.Apify apify = properties.getApify();
        String token = apify.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("missing_token");
        }

        int maxResults = Math.min(Math.max(fetchLimit, 1), 100);

        // 始终拉当日日榜；topic 粗筛只在 Handler.normalize（Actor topicFilter 要 PH 话题名，自由文本易空）
        String body = requestBody(maxResults);
        String response = transport.post(
                apify.getActorId(),
                token.trim(),
                apify.getTimeoutMs(),
                body);
        return resolve(response);
    }

    static String requestBody(int maxResults) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        // cazadores/product-hunt-scraper — daily leaderboard (PH day = US Pacific)
        String day = LocalDate.now(PH_ZONE).toString();
        root.put("mode", "leaderboard");
        root.put("leaderboard", "daily");
        root.put("startDate", day);
        root.put("endDate", day);
        root.put("featuredOnly", true);
        root.put("maxItems", maxResults);
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
