package com.xmut.forma.extension.tool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pi tool {@code fetch_web_page}: guard the URL, fetch one page, write {@code source.md}.
 * The tool JSON never includes the page text.
 */
public final class FetchWebPageToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchWebPageToolHandler.class);

    public static final String TOOL_NAME = "fetch_web_page";
    static final String SOURCE_PATH = "source.md";

    private final PageFetchPort fetchPort;
    private final ObjectMapper objectMapper;

    public FetchWebPageToolHandler(PageFetchPort fetchPort) {
        this(fetchPort, new ObjectMapper());
    }

    FetchWebPageToolHandler(PageFetchPort fetchPort, ObjectMapper objectMapper) {
        this.fetchPort = fetchPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String workspace = LocalFileSupport.workspace(ctx);
            if (workspace == null) {
                return ToolResult.failed(callId, TOOL_NAME, "workspace root missing");
            }

            String url = extractUrl(call);
            WebFetchUrls.validatePublicHttpUrl(url);
            WebFetchHit hit = fetchPort.fetch(url.trim());
            if (hit == null || hit.getText() == null) {
                return failure(callId, "empty_body");
            }
            String text = hit.getText();
            Path path = LocalFileSupport.resolveUnder(Paths.get(workspace), SOURCE_PATH);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, text.getBytes(StandardCharsets.UTF_8));
            ObjectNode root = objectMapper.createObjectNode();
            root.put("ok", true);
            root.put("finalUrl", hit.getFinalUrl() == null ? "" : hit.getFinalUrl());
            root.put("title", hit.getTitle() == null ? "" : hit.getTitle());
            root.put("charCount", text.length());
            root.put("sourcePath", SOURCE_PATH);
            root.put("extractMethod", "apify");
            root.put("truncated", hit.isTruncated());
            root.put("errorCode", "");
            return ToolResult.ok(callId, TOOL_NAME, objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            String code = errorCodeOf(ex);
            log.warn("fetch_web_page failed code={}", code);
            return failure(callId, code);
        }
    }

    private ToolResult failure(String callId, String errorCode) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("ok", false);
            root.put("errorCode", errorCode);
            root.put("message", messageFor(errorCode));
            root.put("finalUrl", "");
            root.put("title", "");
            root.put("charCount", 0);
            root.put("sourcePath", "");
            root.put("extractMethod", "apify");
            root.put("truncated", false);
            return ToolResult.ok(callId, TOOL_NAME, objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            log.warn("fetch_web_page failure json: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, errorCode);
        }
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
        return "打不开或太慢，请换链或直接粘贴正文";
    }

    static String extractUrl(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        JsonNode url = call.getArguments().get("url");
        if (url == null || url.isNull()) {
            return null;
        }
        return url.asText(null);
    }
}
