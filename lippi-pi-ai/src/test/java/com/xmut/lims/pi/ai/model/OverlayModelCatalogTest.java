package com.xmut.lims.pi.ai.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayModelCatalogTest {

    @Test
    void override_pi_default_does_not_change_certificate_ocr() {
        OverlayModelCatalog cat = new OverlayModelCatalog(InMemoryModelCatalog.defaultsWithCertificateOcr());
        cat.putOverride("pi.default", ModelDescriptor.builder()
                .useCase("pi.default").provider("deepseek").model("deepseek-chat")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true).build());
        assertThat(cat.resolve("pi.default").getProvider()).isEqualTo("deepseek");
        assertThat(cat.resolve("certificate-ocr").getProvider()).isEqualTo("dashscope");
    }

    @Test
    void resolve_test_standard_schema_use_case() {
        OverlayModelCatalog cat = new OverlayModelCatalog(InMemoryModelCatalog.defaultsWithCertificateOcr());
        ModelDescriptor desc = cat.resolve(InMemoryModelCatalog.TEST_STANDARD_SCHEMA_USE_CASE);
        assertThat(desc.getProvider()).isEqualTo("deepseek");
        assertThat(desc.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(desc.getModalities()).containsExactly(ModelModality.CHAT);
        assertThat(desc.isSupportsNativeToolCalling()).isTrue();
    }

    @Test
    void override_pi_default_does_not_change_test_standard_schema() {
        OverlayModelCatalog cat = new OverlayModelCatalog(InMemoryModelCatalog.defaultsWithCertificateOcr());
        cat.putOverride("pi.default", ModelDescriptor.builder()
                .useCase("pi.default").provider("dashscope").model("qwen-plus")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true).build());
        assertThat(cat.resolve(InMemoryModelCatalog.TEST_STANDARD_SCHEMA_USE_CASE).getProvider())
                .isEqualTo("deepseek");
    }
}
