package com.xmut.forma.extension.tool.common.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.excerpt.ChunkExcerpt;
import com.xmut.forma.extension.tool.common.excerpt.ExcerptQuoteHelper;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestChunk;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestPrepResult;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestSourcePrep;
import com.xmut.forma.extension.tool.common.web.fetch.WebFetchUrls;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Shared atom chain for competitor/digest ingest: fetch|paste → write source → excerpt.
 * Does not call {@link com.xmut.forma.extension.tool.common.web.crawl.port.SiteCrawlPort}.
 */
public final class PageIngestService {

    public static final String DEFAULT_SOURCE_PATH = "source.md";

    private final PageFetchPort fetchPort;
    private final ChunkExcerptPort excerptPort;
    private final ObjectMapper objectMapper;

    public PageIngestService(PageFetchPort fetchPort, ChunkExcerptPort excerptPort) {
        this(fetchPort, excerptPort, new ObjectMapper());
    }

    PageIngestService(PageFetchPort fetchPort, ChunkExcerptPort excerptPort, ObjectMapper objectMapper) {
        this.fetchPort = fetchPort;
        this.excerptPort = excerptPort;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public PageIngestOutcome ingest(PageIngestRequest request) {
        if (request == null) {
            return PageIngestOutcome.fail("missing_input", "url or paste required");
        }
        String workspace = request.getWorkspace();
        if (!StringUtils.hasText(workspace)) {
            return PageIngestOutcome.fail("workspace_missing", "workspace root missing");
        }
        String sourcePath = StringUtils.hasText(request.getSourcePath())
                ? request.getSourcePath().trim()
                : DEFAULT_SOURCE_PATH;
        try {
            if (StringUtils.hasText(request.getPaste())) {
                return ingestPaste(request, workspace, sourcePath);
            }
            if (StringUtils.hasText(request.getUrl())) {
                return ingestFetch(request, workspace, sourcePath);
            }
            return PageIngestOutcome.fail("missing_input", "url or paste required");
        } catch (IllegalArgumentException ex) {
            return PageIngestOutcome.fail(errorCodeOf(ex), messageFor(errorCodeOf(ex)));
        } catch (Exception ex) {
            return PageIngestOutcome.fail(errorCodeOf(ex), messageFor(errorCodeOf(ex)));
        }
    }

    private PageIngestOutcome ingestPaste(PageIngestRequest request, String workspace, String sourcePath)
            throws Exception {
        String text = request.getPaste().trim();
        writeSource(workspace, sourcePath, text);
        final String excerpt = excerpt("paste", optionalUrl(request.getUrl()), sourcePath, "", text);
        return PageIngestOutcome.ok(excerpt);
    }

    private PageIngestOutcome ingestFetch(PageIngestRequest request, String workspace, String sourcePath)
            throws Exception {
        String url = request.getUrl().trim();
        WebFetchUrls.validatePublicHttpUrl(url);
        if (fetchPort == null) {
            return PageIngestOutcome.fail("apify_error", messageFor("apify_error"));
        }

        WebFetchHit hit = fetchPort.fetch(url);
        if (hit == null || !StringUtils.hasText(hit.getText())) {
            return PageIngestOutcome.fail("empty_body", messageFor("empty_body"));
        }

        String text = hit.getText();
        writeSource(workspace, sourcePath, text);
        String sourceUrl = StringUtils.hasText(hit.getFinalUrl()) ? hit.getFinalUrl() : url;
        String title = hit.getTitle() == null ? "" : hit.getTitle();
        final String excerpt = excerpt("fetch", sourceUrl, sourcePath, title, text);

        return PageIngestOutcome.ok(excerpt);
    }

    private String excerpt(String source, String sourceUrl, String sourcePath, String title, String text) throws Exception {
        TechDigestPrepResult sliced = TechDigestSourcePrep.slice(text);
        List<TechDigestChunk> chunks = sliced.getChunks();
        if (chunks.isEmpty()) {
            throw new RuntimeException(messageFor("empty_body"));
        }

        List<ChunkExcerpt> raw =  excerptPort.excerpt(chunks);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("ok", true);
        root.put("source", source);
        root.put("sourceUrl", sourceUrl == null ? "" : sourceUrl);
        root.put("sourcePath", sourcePath);
        root.put("title", title == null ? "" : title);
        root.put("partialCoverage", sliced.isPartialCoverage());
        ArrayNode excerpts = root.putArray("excerpts");
        for (int i = 0; i < chunks.size(); i++) {
            TechDigestChunk chunk = chunks.get(i);
            String chunkText = chunk == null ? "" : chunk.getText();
            String heading = chunk == null ? "" : chunk.getHeading();
            List<String> quotes = ExcerptQuoteHelper.sanitize(chunkText, rawQuotesAt(raw, i));
            ObjectNode node = excerpts.addObject();
            node.put("heading", heading);
            ArrayNode quoteNodes = node.putArray("quotes");
            for (int q = 0; q < quotes.size(); q++) {
                quoteNodes.add(quotes.get(q));
            }
        }
        if (excerpts.isEmpty()) {
            throw new RuntimeException(messageFor("empty_excerpts"));
        }

        return objectMapper.writeValueAsString(root);
    }

    private static void writeSource(String workspace, String sourcePath, String text) throws Exception {
        Path path = LocalFileSupport.resolveUnder(Paths.get(workspace), sourcePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> rawQuotesAt(List<ChunkExcerpt> raw, int index) {
        if (raw == null || index < 0 || index >= raw.size() || raw.get(index) == null) {
            return Collections.emptyList();
        }
        List<String> quotes = raw.get(index).getQuotes();
        return quotes == null ? Collections.<String>emptyList() : quotes;
    }

    private static String optionalUrl(String url) {
        return StringUtils.hasText(url) ? url.trim() : "";
    }

    static String errorCodeOf(Throwable ex) {
        String msg = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("bad_url")) {
            return "bad_url";
        }
        if (msg.contains("empty_body")) {
            return "empty_body";
        }
        if (msg.contains("missing_token")) {
            return "missing_token";
        }
        if (msg.contains("timeout")) {
            return "timeout";
        }
        if (msg.contains("missing_input")) {
            return "missing_input";
        }
        return "apify_error";
    }

    private static String messageFor(String errorCode) {
        if ("bad_url".equals(errorCode)) {
            return "请贴以 https 开头的公开链接";
        }
        if ("missing_token".equals(errorCode)) {
            return "请配置 APIFY_TOKEN 环境变量";
        }
        if ("empty_body".equals(errorCode)) {
            return "这页几乎没有可读正文（登录墙/反爬仍拦），请粘贴你看到的那段";
        }
        if ("missing_input".equals(errorCode)) {
            return "请提供公开链接或粘贴正文";
        }
        if ("workspace_missing".equals(errorCode)) {
            return "workspace root missing";
        }
        if ("empty_excerpts".equals(errorCode)) {
            return "excerpts empty";
        }
        return "打不开或太慢，请换链或直接粘贴正文";
    }

    /** Request for one ingest call. */
    public static final class PageIngestRequest {
        private final String workspace;
        private final String url;
        private final String paste;
        private final String sourcePath;

        public PageIngestRequest(String workspace, String url, String paste, String sourcePath) {
            this.workspace = workspace;
            this.url = url;
            this.paste = paste;
            this.sourcePath = sourcePath;
        }

        public String getWorkspace() {
            return workspace;
        }

        public String getUrl() {
            return url;
        }

        public String getPaste() {
            return paste;
        }

        public String getSourcePath() {
            return sourcePath;
        }
    }

    /** Outcome: success JSON body or failure code/message. */
    public static final class PageIngestOutcome {
        private final boolean success;
        private final String json;
        private final String errorCode;
        private final String message;

        private PageIngestOutcome(boolean success, String json, String errorCode, String message) {
            this.success = success;
            this.json = json;
            this.errorCode = errorCode;
            this.message = message;
        }

        static PageIngestOutcome ok(String json) {
            return new PageIngestOutcome(true, json, null, null);
        }

        static PageIngestOutcome fail(String errorCode, String message) {
            return new PageIngestOutcome(false, null, errorCode, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getJson() {
            return json;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }
}
