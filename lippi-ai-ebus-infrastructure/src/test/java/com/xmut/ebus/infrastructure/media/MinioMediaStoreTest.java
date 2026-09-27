package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.repository.MediaObjectRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioMediaStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Mock
    private MinioClient minioClient;
    @Mock
    private MediaObjectRepository mediaObjectRepository;

    private MinioMediaStore store;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(minioClient.bucketExists(any())).thenReturn(true);
        store = new MinioMediaStore(
                minioClient,
                mediaObjectRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "ebus-media",
                3600);
    }

    @Test
    void put_savesMetadataAfterPutObject() throws Exception {
        MediaObject media = store.put("user-1", "image/png", new byte[]{1, 2, 3});
        verify(minioClient).putObject(any(PutObjectArgs.class));
        ArgumentCaptor<MediaObject> cap = ArgumentCaptor.forClass(MediaObject.class);
        verify(mediaObjectRepository).save(cap.capture());
        assertEquals(media.getId(), cap.getValue().getId());
        assertTrue(cap.getValue().getObjectKey().contains(media.getId()));
    }

    @Test
    void put_removesObjectWhenMetadataSaveFails() throws Exception {
        doThrow(new RuntimeException("db down")).when(mediaObjectRepository).save(any(MediaObject.class));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                store.put("user-1", "image/png", new byte[]{1, 2, 3}));
        assertEquals(MinioMediaStore.MSG_MEDIA_BUSY, ex.getMessage());
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void issueReadUrl_usesPresign() throws Exception {
        MediaObject meta = MediaObject.create(
                "m1", "user-1", "listing/user-1/m1.png", "image/png", 3, NOW);
        when(mediaObjectRepository.findById("m1")).thenReturn(Optional.of(meta));
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("https://minio.example/m1");

        assertEquals("https://minio.example/m1", store.issueReadUrl("m1"));
    }

    @Test
    void delete_removesObjectAndMetadata() throws Exception {
        MediaObject meta = MediaObject.create(
                "m1", "user-1", "listing/user-1/m1.png", "image/png", 3, NOW);
        when(mediaObjectRepository.findById("m1")).thenReturn(Optional.of(meta));

        store.delete("m1");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(mediaObjectRepository).deleteById("m1");
    }

    @Test
    void delete_skipsWhenMissing() throws Exception {
        when(mediaObjectRepository.findById("missing")).thenReturn(Optional.<MediaObject>empty());
        store.delete("missing");
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(mediaObjectRepository, never()).deleteById(any());
    }
}
