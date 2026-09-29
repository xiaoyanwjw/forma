package com.xmut.ebus.application.business.sku;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * {@link HttpURLConnection}-based Apify REST client.
 */
public final class ApifyHttpUrlConnectionTransport implements ApifyActorTransport {

    private static final String API_BASE = "https://api.apify.com/v2/acts/";

    @Override
    public String postSyncDatasetItems(String actorIdSlash, String token, long timeoutMs, String jsonBody) {
        String actorPath = actorIdSlash.replace('/', '~');
        long timeoutSeconds = Math.max(1L, (timeoutMs + 999L) / 1000L);
        String urlString = API_BASE + actorPath + "/run-sync-get-dataset-items?timeout=" + timeoutSeconds;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(safeInt(timeoutMs));
            connection.setReadTimeout(safeInt(timeoutMs));
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bodyBytes);
            }
            int status = connection.getResponseCode();
            String responseBody = readBody(connection, status >= 200 && status < 300);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("http_error: status=" + status + " body=" + truncate(responseBody));
            }
            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("http_error: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int safeInt(long value) {
        if (value <= 0) {
            return 1;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static String readBody(HttpURLConnection connection, boolean successStream) throws IOException {
        InputStream in = successStream ? connection.getInputStream() : connection.getErrorStream();
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 200) {
            return body;
        }
        return body.substring(0, 200) + "...";
    }
}
