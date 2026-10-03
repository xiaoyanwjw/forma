package com.xmut.forma.pi.ai.provider.openai;

import com.xmut.forma.pi.ai.exception.PiModelAuthException;
import com.xmut.forma.pi.ai.exception.PiModelIoException;
import com.xmut.forma.pi.ai.exception.PiModelRateLimitException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleHttpClientTest {

    private MockWebServer server;
    private OpenAiCompatibleHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OpenAiCompatibleHttpClient(new StubConfig(
                server.url("/v1").toString(), "sk-test", 2_000, "compat-test"));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void post_200_returns_body_and_bearer() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String body = client.complete("{\"model\":\"m\"}", "trace-1", "demo-model");

        assertThat(body).contains("ok");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(req.getHeader("X-Trace-Id")).isEqualTo("trace-1");
    }

    @Test
    void maps_401_using_config_provider_name() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"));

        assertThatThrownBy(() -> client.complete("{}", "trace-1", "demo-model"))
                .isInstanceOf(PiModelAuthException.class)
                .satisfies(ex -> {
                    PiModelAuthException auth = (PiModelAuthException) ex;
                    assertThat(auth.getProvider()).isEqualTo("compat-test");
                    assertThat(auth.getModel()).isEqualTo("demo-model");
                });
    }

    @Test
    void maps_429_and_500() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow"));
        assertThatThrownBy(() -> client.complete("{}", null, "m"))
                .isInstanceOf(PiModelRateLimitException.class);

        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal"));
        assertThatThrownBy(() -> client.complete("{}", null, "m"))
                .isInstanceOf(PiModelIoException.class)
                .satisfies(ex -> assertThat(((PiModelIoException) ex).getProvider()).isEqualTo("compat-test"));
    }

    @Test
    void each_vendor_client_has_own_explicit_connection_pool() {
        OpenAiCompatibleHttpClient other = new OpenAiCompatibleHttpClient(new StubConfig(
                server.url("/v1").toString(), "sk-other", 2_000, "compat-other"));

        assertThat(client.okHttpClient().connectionPool())
                .isNotSameAs(other.okHttpClient().connectionPool());
        assertThat(client.okHttpClient().connectionPool()).isNotNull();
        assertThat(other.okHttpClient().connectionPool()).isNotNull();
    }

    @Test
    void timeout_override_reuses_same_connection_pool() {
        okhttp3.OkHttpClient override = client.okHttpClient().newBuilder()
                .readTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        assertThat(override.connectionPool()).isSameAs(client.okHttpClient().connectionPool());
    }

    @Test
    void stream_reads_sse_data_lines_then_done() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"n\":1}\n\ndata: {\"n\":2}\n\ndata: [DONE]\n\n"));

        java.util.List<String> chunks = new java.util.ArrayList<String>();
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        client.stream("{\"stream\":true}", "trace-s", "m", new SseEventConsumer() {
            @Override
            public void onNext(String jsonObject) {
                chunks.add(jsonObject);
            }

            @Override
            public void onDone() {
                done.set(true);
            }
        });

        assertThat(chunks).containsExactly("{\"n\":1}", "{\"n\":2}");
        assertThat(done).isTrue();
        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("Accept")).isEqualTo("text/event-stream");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(req.getBody().readUtf8()).contains("\"stream\":true");
    }

    @Test
    void stream_maps_401() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("nope"));
        assertThatThrownBy(() -> client.stream("{}", null, "m", new SseEventConsumer() {
            @Override
            public void onNext(String jsonObject) {
            }

            @Override
            public void onDone() {
            }
        })).isInstanceOf(PiModelAuthException.class);
    }

    private static final class StubConfig implements OpenAiCompatibleClientConfig {
        private final String baseUrl;
        private final String apiKey;
        private final Integer timeoutMs;
        private final String providerName;

        private StubConfig(String baseUrl, String apiKey, Integer timeoutMs, String providerName) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.timeoutMs = timeoutMs;
            this.providerName = providerName;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public String getApiKey() {
            return apiKey;
        }

        @Override
        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        @Override
        public String getProviderName() {
            return providerName;
        }
    }
}
