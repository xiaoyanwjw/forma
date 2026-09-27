package com.xmut.ebus.infrastructure.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStoreConfigurationTest {

    @Test
    void minioClient_rejectsBlankCredentials() {
        MediaStoreProperties props = new MediaStoreProperties();
        props.setStore("minio");
        props.getMinio().setEndpoint("http://localhost:9000");
        props.getMinio().setAccessKey("");
        props.getMinio().setSecretKey("");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new MediaStoreConfiguration().minioClient(props));
        assertTrue(ex.getMessage().contains("MINIO_ACCESS_KEY") || ex.getMessage().contains("access-key"));
    }

    @Test
    void minioClient_rejectsBlankEndpoint() {
        MediaStoreProperties props = new MediaStoreProperties();
        props.setStore("minio");
        props.getMinio().setEndpoint(" ");
        props.getMinio().setAccessKey("ak");
        props.getMinio().setSecretKey("sk");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new MediaStoreConfiguration().minioClient(props));
        assertTrue(ex.getMessage().contains("ENDPOINT") || ex.getMessage().contains("endpoint"));
    }
}
