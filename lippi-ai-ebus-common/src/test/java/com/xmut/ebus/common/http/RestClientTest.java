package com.xmut.ebus.common.http;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestClientTest {

    private MockWebServer server;
    private RestClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = RestClient.createDefault();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void postJson_successReturnsBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[{\"ok\":true}]"));

        String body = client.postJson(
                server.url("/v2/acts").toString(),
                "{\"keyword\":\"杯\"}",
                RestClient.bearerJsonHeaders("tok"),
                5_000L);

        assertEquals("[{\"ok\":true}]", body);
        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("Bearer tok", req.getHeader("Authorization"));
        assertTrue(req.getHeader("Content-Type").contains("application/json"));
        assertEquals("{\"keyword\":\"杯\"}", req.getBody().readUtf8());
    }

    @Test
    void postJson_non2xxThrowsWithStatus() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"nope\"}"));

        RestClientException ex = assertThrows(RestClientException.class, () ->
                client.postJson(server.url("/x").toString(), "{}", Collections.<String, String>emptyMap(), 5_000L));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("status=401"));
    }
}
