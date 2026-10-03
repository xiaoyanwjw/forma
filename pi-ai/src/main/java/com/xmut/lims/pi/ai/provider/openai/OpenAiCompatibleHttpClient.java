package com.xmut.lims.pi.ai.provider.openai;

import com.xmut.lims.pi.ai.exception.PiModelAuthException;
import com.xmut.lims.pi.ai.exception.PiModelIoException;
import com.xmut.lims.pi.ai.exception.PiModelRateLimitException;
import com.xmut.lims.pi.ai.exception.PiModelTimeoutException;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 {@code POST /chat/completions}。同步 {@link #complete}；流式 {@link #stream}（SSE）。
 *
 * <p>每个实例自带 {@link ConnectionPool}（DashScope / DeepSeek 各一池，互不共用）。
 * 单次超时覆盖走 {@code client.newBuilder()}，连接池仍复用本实例。
 */
public class OpenAiCompatibleHttpClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleHttpClient.class);

    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

    /** 低于实验室通用 HTTP（32）——模型调用并发更低。OkHttp 默认是 5。 */
    static final int CONNECTION_POOL_MAX_IDLE = 8;
    static final int CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5;

    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final int timeoutMs;
    private final String providerName;

    public OpenAiCompatibleHttpClient(OpenAiCompatibleClientConfig config) {
        this.baseUrl = config.getBaseUrl();
        this.apiKey = config.getApiKey();
        this.timeoutMs = config.getTimeoutMs() == null ? 30_000 : config.getTimeoutMs();
        this.providerName = config.getProviderName();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(
                        CONNECTION_POOL_MAX_IDLE,
                        CONNECTION_POOL_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES))
                .build();
    }

    OkHttpClient okHttpClient() {
        return client;
    }

    public String complete(String payload, String traceId, String model) {
        Request request = newRequest(payload, traceId, false);
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (code == 200) {
                return text;
            }
            throw mapHttpError(code, abbreviate(text, 300), model);
        } catch (SocketTimeoutException e) {
            throw new PiModelTimeoutException(providerName + " 调用超时", providerName, model, e);
        } catch (IOException e) {
            throw new PiModelIoException(providerName + " 网络异常", providerName, model, e);
        }
    }

    /**
     * SSE：{@code data:} JSON 交给 consumer；{@code data: [DONE]} 后 {@link SseEventConsumer#onDone()}。
     */
    public void stream(String payload, String traceId, String model, SseEventConsumer consumer) {
        Request request = newRequest(payload, traceId, true);
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            ResponseBody body = response.body();
            if (code != 200) {
                String text = body == null ? "" : body.string();
                throw mapHttpError(code, abbreviate(text, 300), model);
            }
            if (body == null) {
                throw new PiModelIoException(providerName + " 空响应", providerName, model, null);
            }

            on(body, consumer);
        } catch (SocketTimeoutException e) {
            throw new PiModelTimeoutException(providerName + " 调用超时", providerName, model, e);
        } catch (IOException e) {
            throw new PiModelIoException(providerName + " 网络异常", providerName, model, e);
        }
    }

    private Request newRequest(String payload, String traceId, boolean sse) {
        Request.Builder b = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .post(RequestBody.create(payload, APPLICATION_JSON));
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            b.addHeader("Authorization", "Bearer " + apiKey.trim());
        }
        if (traceId != null) {
            b.addHeader("X-Trace-Id", traceId);
        }
        if (sse) {
            b.addHeader("Accept", "text/event-stream");
        }
        return b.build();
    }

    static void on(ResponseBody body, SseEventConsumer consumer) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (notify(data.toString(), consumer)) {
                    return;
                }
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                String piece = line.substring(5);
                if (piece.startsWith(" ")) {
                    piece = piece.substring(1);
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(piece);
            }
        }
        if (data.length() > 0 && notify(data.toString(), consumer)) {
            return;
        }
        consumer.onDone();
    }

    /** @return true 表示已 [DONE] */
    private static boolean notify(String payload, SseEventConsumer consumer) {
        String t = payload.trim();
        if (t.isEmpty()) {
            return false;
        }
        if ("[DONE]".equals(t)) {
            consumer.onDone();
            return true;
        }
        consumer.onNext(t);
        return false;
    }

    private RuntimeException mapHttpError(int httpCode, String brief, String model) {
        if (httpCode == 401) {
            log.warn("auth_failed provider={} model={}", providerName, model);
            return new PiModelAuthException(providerName + " 鉴权失败 (401)", providerName, model);
        }
        if (httpCode == 429) {
            return new PiModelRateLimitException(providerName + " 限流 (429): " + brief, providerName, model);
        }
        if (httpCode == 408) {
            return new PiModelTimeoutException(providerName + " 服务端超时 (408): " + brief, providerName, model, null);
        }
        return new PiModelIoException(
                providerName + " HTTP " + httpCode + ": " + brief, providerName, model, null);
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').replace('\r', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
