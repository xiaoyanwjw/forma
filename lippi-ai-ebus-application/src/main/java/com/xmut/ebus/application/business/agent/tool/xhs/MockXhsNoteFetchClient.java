package com.xmut.ebus.application.business.agent.tool.xhs;

import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Fixed mock note detail. Echoes the requested URL; body is never empty.
 */
public final class MockXhsNoteFetchClient implements XhsNoteFetchPort {

    static final String MOCK_TITLE = "Mock 小红书笔记标题";
    static final String MOCK_BODY = "Mock 小红书笔记正文：硅胶沥水垫种草分享，适合租房党厨房收纳。";

    @Override
    public XhsNoteFetchHit fetch(String noteRef) {
        if (!StringUtils.hasText(noteRef)) {
            throw new IllegalStateException("note_ref_required");
        }
        String url = noteRef.trim();
        return new XhsNoteFetchHit(
                MOCK_TITLE,
                MOCK_BODY,
                url,
                "mock_author",
                Arrays.asList("种草", "收纳"));
    }
}
