package com.xmut.forma.common.http;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp3 的轻量 REST 客户端：共享 {@link ConnectionPool}，单次请求可覆盖超时。
 */
public final class RestClient {

    public static final MediaType JSON_UTF8 = MediaType.parse("application/json; charset=utf-8");

    /** 与实验室通用 HTTP 池对齐量级；OkHttp 默认 idle=5。 */
    public static final int DEFAULT_MAX_IDLE_CONNECTIONS = 32;
    public static final int DEFAULT_KEEP_ALIVE_MINUTES = 5;
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final OkHttpClient client;

    public RestClient(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /** 默认连接池 + 默认超时的客户端。 */
    public static RestClient createDefault() {
        return new RestClient(newBuilderWithPool(
                DEFAULT_MAX_IDLE_CONNECTIONS,
                DEFAULT_KEEP_ALIVE_MINUTES,
                DEFAULT_TIMEOUT_MS).build());
    }

    public static OkHttpClient.Builder newBuilderWithPool(int maxIdle,
                                                          int keepAliveMinutes,
                                                          long timeoutMs) {
        long safeTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        return new OkHttpClient.Builder()
                .connectTimeout(safeTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(safeTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(safeTimeout, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(
                        Math.max(1, maxIdle),
                        Math.max(1, keepAliveMinutes),
                        TimeUnit.MINUTES));
    }

    public OkHttpClient okHttpClient() {
        return client;
    }

    /**
     * POST JSON；非 2xx 抛 {@link RestClientException}（含 status 与截断 body）。
     *
     * @param timeoutMs ≤0 时沿用 client 默认超时；&gt;0 时对本请求覆盖超时（仍复用连接池）
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers, long timeoutMs) {
        Objects.requireNonNull(url, "url");
        String body = jsonBody == null ? "" : jsonBody;
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON_UTF8));
        applyHeaders(builder, headers);
        return execute(builder.build(), timeoutMs);
    }

    public String postJson(String url, String jsonBody, long timeoutMs) {
        return postJson(url, jsonBody, Collections.<String, String>emptyMap(), timeoutMs);
    }

    private static void applyHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Headers.Builder hb = new Headers.Builder();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            hb.add(e.getKey(), e.getValue());
        }
        builder.headers(hb.build());
    }

    private String execute(Request request, long timeoutMs) {
        OkHttpClient callClient = clientForTimeout(timeoutMs);
        try (Response response = callClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            int code = response.code();
            if (code < 200 || code >= 300) {
                throw new RestClientException(code, text);
            }
            return text;
        } catch (RestClientException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new RestClientException("timeout: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RestClientException("io_error: " + e.getMessage(), e);
        }
    }

    private OkHttpClient clientForTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return client;
        }
        return client.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /** Bearer 头便捷构造。 */
    public static Map<String, String> bearerJsonHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
        headers.put("Content-Type", "application/json; charset=UTF-8");
        return headers;
    }
}
