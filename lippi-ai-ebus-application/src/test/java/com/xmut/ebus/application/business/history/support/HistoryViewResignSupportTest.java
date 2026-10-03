package com.xmut.ebus.application.business.history.support;

import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryViewResignSupportTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private HistoryViewResignSupport support;

    @BeforeEach
    void setUp() {
        support = new HistoryViewResignSupport(new FreshUrlMediaStore());
    }

    @Test
    void resignView_refreshesImgSrcByMediaObjectId() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 2);
        view.put("format", "html");
        view.put("title", "t");
        view.put("content", "<img data-adam-media-object-id=\"m-1\" src=\"https://old.example/x\">");
        Map<String, Object> out = support.resignView(view, "user-1");
        assertTrue(String.valueOf(out.get("content")).contains("https://fresh.example/y"));
    }

    private static final class FreshUrlMediaStore implements MediaStore {
        @Override
        public MediaObject put(String userId, String contentType, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issueReadUrl(String mediaObjectId) {
            return "https://fresh.example/y";
        }

        @Override
        public Optional<MediaObject> findById(String mediaObjectId) {
            if ("m-1".equals(mediaObjectId)) {
                return Optional.of(MediaObject.create("m-1", "user-1", "k/m-1", "image/png", 1, NOW));
            }
            return Optional.empty();
        }

        @Override
        public void delete(String mediaObjectId) {
            throw new UnsupportedOperationException();
        }
    }
}
