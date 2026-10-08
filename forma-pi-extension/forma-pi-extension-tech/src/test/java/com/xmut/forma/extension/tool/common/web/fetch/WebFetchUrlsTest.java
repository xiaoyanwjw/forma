package com.xmut.forma.extension.tool.common.web.fetch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebFetchUrlsTest {

    @Test
    void https_public_host_is_allowed() {
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("https://example.com/product"));
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("http://example.com:8080/a?q=1"));
    }

    @Test
    void localhost_and_loopback_are_rejected() {
        assertBadUrl("http://localhost/admin");
        assertBadUrl("http://LOCALHOST/admin");
        assertBadUrl("https://127.0.0.1/secret");
        assertBadUrl("http://[::1]/secret");
    }

    @Test
    void private_and_link_local_literal_ipv4_are_rejected() {
        assertBadUrl("http://10.1.2.3/internal");
        assertBadUrl("https://10.255.255.255/");
        assertBadUrl("http://192.168.0.1/");
        assertBadUrl("http://172.16.0.1/");
        assertBadUrl("http://172.31.255.255/");
        assertBadUrl("http://169.254.1.1/");
    }

    @Test
    void public_neighbors_of_private_ranges_are_allowed() {
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("https://11.0.0.1/"));
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("https://172.15.255.255/"));
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("https://172.32.0.1/"));
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl("https://192.169.0.1/"));
    }

    @Test
    void local_suffix_and_non_http_schemes_are_rejected() {
        assertBadUrl("http://printer.local/status");
        assertBadUrl("http://files.LOCAL/a");
        assertBadUrl("file:///etc/passwd");
        assertBadUrl("javascript:alert(1)");
        assertBadUrl("ftp://example.com/a");
    }

    @Test
    void blank_and_overlong_urls_are_rejected() {
        assertBadUrl(null);
        assertBadUrl("  ");
        String prefix = "https://example.com/";
        StringBuilder longUrl = new StringBuilder(prefix);
        while (longUrl.length() < 2049) {
            longUrl.append('a');
        }
        assertBadUrl(longUrl.toString());
        assertDoesNotThrow(() -> WebFetchUrls.validatePublicHttpUrl(prefix + repeat('b', 2048 - prefix.length())));
    }

    private static void assertBadUrl(String url) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WebFetchUrls.validatePublicHttpUrl(url));
        assertEquals("bad_url", ex.getMessage());
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
