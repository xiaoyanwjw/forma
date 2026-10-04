package com.xmut.forma.extension.tool.web;

import java.net.URI;
import java.util.Locale;

/**
 * SSRF guard for {@code fetch_web_page}. Literal private IPv4 only; no DNS lookup.
 */
public final class WebFetchUrls {

    static final int MAX_LENGTH = 2048;

    private WebFetchUrls() {
    }

    public static void validatePublicHttpUrl(String url) {
        if (url == null) {
            throw bad();
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw bad();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception ex) {
            throw bad();
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw bad();
        }
        String schemeLower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(schemeLower) && !"https".equals(schemeLower)) {
            throw bad();
        }
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw bad();
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty() || "localhost".equals(host) || isLoopbackV6(host) || host.endsWith(".local")) {
            throw bad();
        }
        int[] ipv4 = literalIpv4(host);
        if (ipv4 != null && isForbiddenIpv4(ipv4)) {
            throw bad();
        }
    }

    private static boolean isLoopbackV6(String host) {
        return "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host);
    }

    private static boolean isForbiddenIpv4(int[] o) {
        if (o[0] == 10) {
            return true;
        }
        if (o[0] == 127 && o[1] == 0 && o[2] == 0 && o[3] == 1) {
            return true;
        }
        if (o[0] == 192 && o[1] == 168) {
            return true;
        }
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) {
            return true;
        }
        return o[0] == 169 && o[1] == 254;
    }

    private static int[] literalIpv4(String host) {
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return null;
            }
        }
        if (dots != 3) {
            return null;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] oct = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) {
                return null;
            }
            int value = Integer.parseInt(parts[i]);
            if (value > 255) {
                return null;
            }
            oct[i] = value;
        }
        return oct;
    }

    private static IllegalArgumentException bad() {
        return new IllegalArgumentException("bad_url");
    }
}
