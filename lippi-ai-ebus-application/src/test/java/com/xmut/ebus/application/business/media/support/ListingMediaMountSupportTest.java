package com.xmut.ebus.application.business.media.support;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingMediaMountSupportTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private TrackingMediaStore mediaStore;
    private ListingMediaMountSupport support;

    @BeforeEach
    void setUp() {
        mediaStore = new TrackingMediaStore();
        support = new ListingMediaMountSupport(mediaStore);
    }

    @Test
    void mount_synthesizesHeroWhenNoMediaBlock() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 1);
        view.put("title", "listing");
        view.put("blocks", new ArrayList<Object>());

        ListingMediaMountSupport.MountedListingMedia mounted =
                support.mountSystemPlaceholder("u1", view, Collections.<String, Object>singletonMap("heroPlan", "方案A"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) mounted.getView().get("blocks");
        assertEquals(1, blocks.size());
        assertEquals("media", blocks.get(0).get("type"));
        assertEquals("hero", blocks.get(0).get("role"));
        assertEquals(mounted.getMediaObjectId(), blocks.get(0).get("mediaObjectId"));
        assertTrue(String.valueOf(blocks.get(0).get("src")).startsWith("data:"));
    }

    @Test
    void mount_prefersHeroWhenNonHeroMediaComesFirst() {
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> gallery = new LinkedHashMap<String, Object>();
        gallery.put("type", "media");
        gallery.put("role", "gallery");
        gallery.put("placeholder", "辅图");
        blocks.add(gallery);
        Map<String, Object> hero = new LinkedHashMap<String, Object>();
        hero.put("type", "media");
        hero.put("role", "hero");
        hero.put("placeholder", "主图方案");
        blocks.add(hero);
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("blocks", blocks);

        ListingMediaMountSupport.MountedListingMedia mounted =
                support.mountSystemPlaceholder("u1", view, Collections.<String, Object>emptyMap());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) mounted.getView().get("blocks");
        assertEquals("gallery", out.get(0).get("role"));
        assertFalse(out.get(0).containsKey("mediaObjectId"));
        assertEquals("hero", out.get(1).get("role"));
        assertEquals(mounted.getMediaObjectId(), out.get(1).get("mediaObjectId"));
        assertEquals(mounted.getMediaObjectId(),
                ((List<?>) mounted.getBusinessPayload().get("mediaObjectIds")).get(0));
    }

    @Test
    void injectHeroMedia_patchesV2HtmlContent() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 2);
        view.put("title", "上架");
        view.put("format", "html");
        view.put("content", "<article class=\"markdown-body\"><h1>T</h1></article>");
        ListingMediaMountSupport.injectHeroMedia(view, "m-1", "https://cdn.example/a.png", new LinkedHashMap<String, Object>());
        String content = String.valueOf(view.get("content"));
        assertTrue(content.contains("data-adam-media-object-id=\"m-1\""));
        assertTrue(content.contains("data-adam-media-role=\"hero\""));
        assertTrue(content.contains("https://cdn.example/a.png"));
        assertFalse(view.containsKey("blocks"));
    }

    @Test
    void mount_deletesOrphanWhenIssueReadUrlFails() {
        mediaStore.failIssue = true;
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("blocks", new ArrayList<Object>());

        assertThrows(BusinessException.class, () ->
                support.mountSystemPlaceholder("u1", view, Collections.<String, Object>emptyMap()));
        assertEquals(1, mediaStore.putCount.get());
        assertEquals(1, mediaStore.deleteCount.get());
        assertTrue(mediaStore.byId.isEmpty());
    }

    private static final class TrackingMediaStore implements MediaStore {
        private final ConcurrentHashMap<String, MediaObject> byId = new ConcurrentHashMap<String, MediaObject>();
        private final AtomicInteger putCount = new AtomicInteger();
        private final AtomicInteger deleteCount = new AtomicInteger();
        private boolean failIssue;

        @Override
        public MediaObject put(String userId, String contentType, byte[] bytes) {
            putCount.incrementAndGet();
            String id = UUID.randomUUID().toString();
            MediaObject media = MediaObject.create(id, userId, "k/" + id, contentType, 1, NOW);
            byId.put(id, media);
            return media;
        }

        @Override
        public String issueReadUrl(String mediaObjectId) {
            if (failIssue) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, ListingMediaMountSupport.MSG_MEDIA_BUSY);
            }
            return "data:image/png;base64,AAAA";
        }

        @Override
        public Optional<MediaObject> findById(String mediaObjectId) {
            return Optional.ofNullable(byId.get(mediaObjectId));
        }

        @Override
        public void delete(String mediaObjectId) {
            deleteCount.incrementAndGet();
            byId.remove(mediaObjectId);
        }
    }
}
