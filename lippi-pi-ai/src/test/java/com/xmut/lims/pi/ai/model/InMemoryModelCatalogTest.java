package com.xmut.lims.pi.ai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryModelCatalogTest {

    @Test
    void defaultsWithCertificateOcr_resolves_walk_in_paper_import() {
        InMemoryModelCatalog catalog = InMemoryModelCatalog.defaultsWithCertificateOcr();
        assertThat(catalog.resolve(InMemoryModelCatalog.WALK_IN_IMPORT_USE_CASE).getUseCase())
                .isEqualTo("walk-in-import");
        assertThat(catalog.resolve("walk-in-import").getModel())
                .isEqualTo("deepseek-v4-flash");
    }
}
