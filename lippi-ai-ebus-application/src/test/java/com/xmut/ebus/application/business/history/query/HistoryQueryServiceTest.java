package com.xmut.ebus.application.business.history.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.dto.HistoryArtifactSummaryDTO;
import com.xmut.ebus.application.business.history.support.HistoryViewResignSupport;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
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
    private MediaStore mediaStore;
    private HistoryQueryService service;

    @BeforeEach
    void setUp() {
        artifactRepository = mock(ArtifactRepository.class);
        mediaStore = mock(MediaStore.class);
        service = new HistoryQueryService(
                artifactRepository,
                new HistoryViewResignSupport(mediaStore),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listUsesSixtyDayWindowAndHistoryTypes() {
        when(artifactRepository.listByUserSince(eq(USER), any(Instant.class), any(), isNull()))
                .thenReturn(Collections.singletonList(picklist(NOW.minus(1, ChronoUnit.DAYS))));

        List<HistoryArtifactSummaryDTO> list = service.list(USER, null);
        assertEquals(1, list.size());
        assertEquals("picklist", list.get(0).getArtifactType());

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<ArtifactType>> typesCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(artifactRepository).listByUserSince(
                eq(USER), sinceCaptor.capture(), typesCaptor.capture(), isNull());
        assertEquals(NOW.minus(60, ChronoUnit.DAYS), sinceCaptor.getValue());
        assertTrue(typesCaptor.getValue().contains(ArtifactType.PICKLIST));
        assertTrue(typesCaptor.getValue().contains(ArtifactType.SKU));
    }

    @Test
    void listPassesSceneCodeFilter() {
        when(artifactRepository.listByUserSince(eq(USER), any(Instant.class), any(), eq("ecommerce")))
                .thenReturn(Collections.emptyList());
        assertTrue(service.list(USER, "ecommerce").isEmpty());
        verify(artifactRepository).listByUserSince(eq(USER), any(Instant.class), any(), eq("ecommerce"));
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
                "sku-1", USER, "run-2", ArtifactType.SKU, "ecommerce",
                null, "Listing", payload, NOW.minus(2, ChronoUnit.DAYS));
        when(artifactRepository.findById("sku-1")).thenReturn(Optional.of(sku));
        when(mediaStore.findById("m1")).thenReturn(Optional.of(
                MediaObject.create("m1", USER, "k1", "image/png", 1L, NOW)));
        when(mediaStore.issueReadUrl("m1")).thenReturn("https://signed.example/m1");

        HistoryArtifactDetailDTO detail = service.findById(USER, "sku-1");
        assertEquals("sku", detail.getArtifactType());
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
                "sku-2", USER, "run-3", ArtifactType.SKU, "ecommerce",
                null, "Listing", payload, NOW.minus(1, ChronoUnit.DAYS));
        when(artifactRepository.findById("sku-2")).thenReturn(Optional.of(sku));
        when(mediaStore.findById("m1")).thenReturn(Optional.of(
                MediaObject.create("m1", USER, "k1", "image/png", 1L, NOW)));
        when(mediaStore.issueReadUrl("m1")).thenThrow(new RuntimeException("presign boom"));

        HistoryArtifactDetailDTO detail = service.findById(USER, "sku-2");
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

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(USER, "art-1"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByIdRejectsOutsideWindow() {
        Artifact old = picklist(NOW.minus(61, ChronoUnit.DAYS));
        when(artifactRepository.findById("art-1")).thenReturn(Optional.of(old));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(USER, "art-1"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByIdRejectsChatType() {
        Artifact chat = Artifact.create(
                "chat-1", USER, "run-c", ArtifactType.CHAT, "ecommerce",
                null, "聊", "{}", NOW);
        when(artifactRepository.findById("chat-1")).thenReturn(Optional.of(chat));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(USER, "chat-1"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    private static Artifact picklist(Instant createdAt) {
        return Artifact.create(
                "art-1", USER, "run-1", ArtifactType.PICKLIST, "ecommerce",
                null, "选品", "{\"view\":{\"blocks\":[]},\"data\":{}}", createdAt);
    }
}
