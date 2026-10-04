package com.xmut.forma.extension.tool.web.client;

import com.xmut.forma.extension.tool.web.WebFetchUrls;
import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import com.xmut.forma.extension.tool.web.port.WebFetchPort;

/**
 * Fixed public-page fixture used when {@code forma.web-fetch.client} is not apify.
 */
public final class MockWebFetchClient implements WebFetchPort {

    static final String MOCK_TITLE = "Mock 科技页";
    /** Appears once so callers can prove tool JSON does not echo the body. */
    static final String MARKER = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉";

    private static final String MOCK_TEXT = buildText();

    @Override
    public WebFetchHit fetch(String url) {
        WebFetchUrls.validatePublicHttpUrl(url);
        return new WebFetchHit(url.trim(), MOCK_TITLE, MOCK_TEXT, false);
    }

    private static String buildText() {
        String unit = "本地模拟科技页正文，说明一款可以在个人电脑上运行的推理工具，文档要求足够内存，不承诺具体跑分。";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 480) {
            sb.append(unit);
        }
        sb.insert(Math.min(180, sb.length()), MARKER);
        return sb.toString();
    }
}
