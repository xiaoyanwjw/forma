package com.xmut.forma.application.business.history.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.forma.application.business.history.dto.HistoryArtifactSummaryDTO;
import com.xmut.forma.application.business.history.support.HistoryViewResignSupport;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.domain.business.agent.model.GenerationRun;
import com.xmut.forma.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.forma.domain.business.media.model.MediaObject;
import com.xmut.forma.domain.business.media.store.MediaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final String USER = "user-1";

    private ArtifactRepository artifactRepository;
    private GenerationRunRepository generationRunRepository;
    private MediaStore mediaStore;
    private HistoryQueryService service;

    @BeforeEach
    void setUp() {
        artifactRepository = mock(ArtifactRepository.class);
        generationRunRepository = mock(GenerationRunRepository.class);
        mediaStore = mock(MediaStore.class);
        service = new HistoryQueryService(
                artifactRepository,
                generationRunRepository,
                new HistoryViewResignSupport(mediaStore),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> java.util.Arrays.asList("chat", "listing_plan"));
        when(generationRunRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void listUsesSixtyDayWindowAndExcludesInternalTypes() {
        when(artifactRepository.listByUserSince(eq(USER), any(Instant.class), isNull(), isNull()))
                .thenReturn(Collections.singletonList(picklist(NOW.minus(1, ChronoUnit.DAYS))));

        List<HistoryArtifactSummaryDTO> list = service.list(
                HistoryListQuery.builder().userId(USER).sceneCode(null).build());
        assertEquals(1, list.size());
        assertEquals("picklist", list.get(0).getArtifactType());

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(artifactRepository).listByUserSince(
                eq(USER), sinceCaptor.capture(), isNull(), isNull());
        assertEquals(NOW.minus(60, ChronoUnit.DAYS), sinceCaptor.getValue());
    }

    @Test
    void listPassesSceneCodeFilter() {
        when(artifactRepository.listByUserSince(eq(USER), any(Instant.class), isNull(), eq("ecommerce")))
                .thenReturn(Collections.emptyList());
        assertTrue(service.list(listQuery("ecommerce")).isEmpty());
        verify(artifactRepository).listByUserSince(eq(USER), any(Instant.class), isNull(), eq("ecommerce"));
    }

    @Test
    void listPassesXiaohongshuSceneFilter() {
        when(artifactRepository.listByUserSince(eq(USER), any(Instant.class), isNull(), eq("xiaohongshu")))
                .thenReturn(Collections.singletonList(xhsBreak(NOW.minus(1, ChronoUnit.DAYS))));
        List<HistoryArtifactSummaryDTO> list = service.list(listQuery("xiaohongshu"));
        assertEquals(1, list.size());
        assertEquals("xhs_break", list.get(0).getArtifactType());
        assertEquals("xiaohongshu", list.get(0).getSceneCode());
        verify(artifactRepository).listByUserSince(eq(USER), any(Instant.class), isNull(), eq("xiaohongshu"));
    }

    @Test
    void findByIdAllowsOpenSceneType() {
        ArtifactType open = ArtifactType.fromCode("future_digest");
        Artifact artifact = Artifact.create(
                "future-1", USER, "run-f", open, "future",
                null, "新场景",
                "{\"view\":{\"title\":\"开类型\",\"format\":\"html\",\"content\":\"<p>x</p>\"},\"data\":{}}",
                NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("future-1")).thenReturn(Optional.of(artifact));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("future-1"));
        assertEquals("future_digest", detail.getArtifactType());
        assertEquals("开类型", detail.getView().get("title"));
    }

    @Test
    void findByIdAllowsTechDigest() {
        Artifact digest = Artifact.create(
                "digest-1", USER, "run-td", ArtifactType.fromCode("tech_digest"), "tech_digest",
                null, "科技速读",
                "{\"view\":{\"title\":\"速读标题\",\"format\":\"html\",\"content\":\"<p>x</p>\"},\"data\":{}}",
                NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("digest-1")).thenReturn(Optional.of(digest));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("digest-1"));
        assertEquals("tech_digest", detail.getArtifactType());
        assertEquals("tech_digest", detail.getSceneCode());
        assertEquals("速读标题", detail.getView().get("title"));
    }

    @Test
    void findByIdAllowsXhsBreak() {
        Artifact breakArt = xhsBreak(NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("xhs-break-1")).thenReturn(Optional.of(breakArt));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("xhs-break-1"));
        assertEquals("xhs_break", detail.getArtifactType());
        assertEquals("xiaohongshu", detail.getSceneCode());
        assertEquals("骨架一行", detail.getView().get("title"));
    }

    @Test
    void findByIdResignsListingMediaSrc() {
        String payload = "{"
                + "\"view\":{\"kind\":\"listingPreview\",\"blocks\":["
                + "{\"type\":\"media\",\"role\":\"hero\",\"mediaObjectId\":\"m1\",\"src\":\"stale\"}"
                + "]},"
                + "\"data\":{}"
                + "}";
        Artifact sku = Artifact.create(
                "sku-1", USER, "run-2", ArtifactType.fromCode("sku"), "ecommerce",
                null, "Listing", payload, NOW.minus(2, ChronoUnit.DAYS));
        when(artifactRepository.findById("sku-1")).thenReturn(Optional.of(sku));
        when(mediaStore.findById("m1")).thenReturn(Optional.of(
                MediaObject.create("m1", USER, "k1", "image/png", 1L, NOW)));
        when(mediaStore.issueReadUrl("m1")).thenReturn("https://signed.example/m1");

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("sku-1"));
        assertEquals("sku", detail.getArtifactType());
        assertNull(detail.getSessionId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) detail.getView().get("blocks");
        assertEquals("https://signed.example/m1", blocks.get(0).get("src"));
        assertEquals("m1", blocks.get(0).get("mediaObjectId"));
    }

    @Test
    void findByIdKeepsStaleSrcWhenResignThrows() {
        String payload = "{"
                + "\"view\":{\"kind\":\"listingPreview\",\"title\":\"t\",\"blocks\":["
                + "{\"type\":\"media\",\"role\":\"hero\",\"mediaObjectId\":\"m1\",\"src\":\"stale\"},"
                + "{\"type\":\"section\",\"heading\":\"详情标题\",\"body\":\"正文\"}"
                + "]},"
                + "\"data\":{}"
                + "}";
        Artifact sku = Artifact.create(
                "sku-2", USER, "run-3", ArtifactType.fromCode("sku"), "ecommerce",
                null, "Listing", payload, NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("sku-2")).thenReturn(Optional.of(sku));
        when(mediaStore.findById("m1")).thenReturn(Optional.of(
                MediaObject.create("m1", USER, "k1", "image/png", 1L, NOW)));
        when(mediaStore.issueReadUrl("m1")).thenThrow(new RuntimeException("presign boom"));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("sku-2"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) detail.getView().get("blocks");
        assertEquals(2, blocks.size());
        assertEquals("stale", blocks.get(0).get("src"));
        assertEquals("section", blocks.get(1).get("type"));
        assertEquals("正文", blocks.get(1).get("body"));
    }

    @Test
    void findByIdRejectsOtherUser() {
        Artifact other = picklist(NOW);
        other.setUserId("other");
        when(artifactRepository.findById("art-1")).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(artifactQuery("art-1")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByIdRejectsOutsideWindow() {
        Artifact old = picklist(NOW.minus(61, ChronoUnit.DAYS));
        when(artifactRepository.findById("art-1")).thenReturn(Optional.of(old));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(artifactQuery("art-1")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByIdRejectsChatType() {
        Artifact chat = Artifact.create(
                "chat-1", USER, "run-c", ArtifactType.fromCode("chat"), "ecommerce",
                null, "聊", "{}", NOW);
        when(artifactRepository.findById("chat-1")).thenReturn(Optional.of(chat));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(artifactQuery("chat-1")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByIdAttachesSessionIdFromGenerationRun() {
        Artifact sku = Artifact.create(
                "sku-1", USER, "run-2", ArtifactType.fromCode("sku"), "ecommerce",
                null, "Listing", "{\"view\":{},\"data\":{}}", NOW.minus(2, ChronoUnit.DAYS));
        when(artifactRepository.findById("sku-1")).thenReturn(Optional.of(sku));
        GenerationRun run = GenerationRun.start(
                "run-2", USER, "hold-1", "sess-abc", "scene-1", "ecommerce", NOW);
        when(generationRunRepository.findById("run-2")).thenReturn(Optional.of(run));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("sku-1"));
        assertEquals("sess-abc", detail.getSessionId());
    }

    @Test
    void findByIdLeavesSessionIdNullWhenRunMissing() {
        Artifact picklist = picklist(NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("art-1")).thenReturn(Optional.of(picklist));

        HistoryArtifactDetailDTO detail = service.findById(artifactQuery("art-1"));
        assertNull(detail.getSessionId());
    }

    private static HistoryListQuery listQuery(String sceneCode) {
        return HistoryListQuery.builder().userId(USER).sceneCode(sceneCode).build();
    }

    private static HistoryArtifactQuery artifactQuery(String artifactId) {
        return HistoryArtifactQuery.builder().userId(USER).artifactId(artifactId).build();
    }

    private static Artifact picklist(Instant createdAt) {
        return Artifact.create(
                "art-1", USER, "run-1", ArtifactType.fromCode("picklist"), "ecommerce",
                null, "选品", "{\"view\":{\"blocks\":[]},\"data\":{}}", createdAt);
    }

    private static Artifact xhsBreak(Instant createdAt) {
        return Artifact.create(
                "xhs-break-1", USER, "run-xhs", ArtifactType.fromCode("xhs_break"), "xiaohongshu",
                null, "爆文拆解",
                "{\"view\":{\"title\":\"骨架一行\",\"blocks\":[]},\"data\":{\"skeleton\":\"场景痛点一句\"}}",
                createdAt);
    }
}
