package com.xmut.ebus.common.http;

/**
 * {@link RestClient} 非 2xx / 网络失败。
 */
public final class RestClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public RestClientException(int statusCode, String responseBody) {
        super("http_error: status=" + statusCode + " body=" + truncate(responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public RestClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = "";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    static String truncate(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 200) {
            return body;
        }
        return body.substring(0, 200) + "...";
    }
}
