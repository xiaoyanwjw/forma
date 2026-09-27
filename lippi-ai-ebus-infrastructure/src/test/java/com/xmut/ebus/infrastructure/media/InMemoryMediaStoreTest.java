package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.domain.business.media.model.MediaObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMediaStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    @Test
    void putAndIssueReadUrl_returnsDataUri() {
        InMemoryMediaStore store = new InMemoryMediaStore(Clock.fixed(NOW, ZoneOffset.UTC));
        MediaObject media = store.put("user-1", "image/png", new byte[]{1, 2, 3});
        String url = store.issueReadUrl(media.getId());
        assertTrue(url.startsWith("data:image/png;base64,"));
        assertEquals(media.getId(), store.findById(media.getId()).get().getId());
    }
}
