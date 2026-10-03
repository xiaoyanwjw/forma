package com.xmut.lims.pi.ai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryModelCatalogTest {

    @Test
    void defaults_piDefault_routesToDeepSeek() {
        InMemoryModelCatalog catalog = InMemoryModelCatalog.defaults();
        assertThat(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE).getProvider())
                .isEqualTo("deepseek");
        assertThat(catalog.resolve("pi.default").getModel())
                .isEqualTo("deepseek-v4-flash");
        assertThat(catalog.registeredUseCases()).containsExactly("pi.default");
    }

    @Test
    void unknownUseCase_throws() {
        InMemoryModelCatalog catalog = InMemoryModelCatalog.defaults();
        assertThatThrownBy(() -> catalog.resolve("certificate-ocr"))
                .isInstanceOf(UnsupportedModelException.class)
                .hasMessageContaining("certificate-ocr");
    }
}
