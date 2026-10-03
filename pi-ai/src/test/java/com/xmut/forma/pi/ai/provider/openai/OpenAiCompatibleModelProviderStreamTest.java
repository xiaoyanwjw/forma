package com.xmut.forma.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.model.InMemoryModelCatalog;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.model.TokenConsumer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleModelProviderStreamTest {

    private MockWebServer server;
    private OpenAiCompatibleModelProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OpenAiCompatibleHttpClient http = new OpenAiCompatibleHttpClient(new OpenAiCompatibleClientConfig() {
            @Override
            public String getBaseUrl() {
                return server.url("/v1").toString();
            }

            @Override
            public String getApiKey() {
                return "sk-test";
            }

            @Override
            public Integer getTimeoutMs() {
                return 2_000;
            }

            @Override
            public String getProviderName() {
                return "dashscope";
            }
        });
        provider = new OpenAiCompatibleModelProvider(
                http, new ObjectMapper(), InMemoryModelCatalog.defaults());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void stream_sends_stream_true_and_emits_text_deltas() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"He\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"llo\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n"));

        List<String> deltas = new ArrayList<String>();
        AtomicReference<ModelResponse> done = new AtomicReference<ModelResponse>();
        provider.stream(ModelRequest.builder()
                .useCase("pi.default")
                .messages(Collections.singletonList(Message.user("hi")))
                .build(), new TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
                done.set(response);
            }
        });

        assertThat(deltas).containsExactly("He", "llo");
        assertThat(done.get().getContent()).isEqualTo("Hello");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).contains("\"stream\":true");
    }
}
