package com.xmut.lims.pi.ai.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayModelCatalogTest {

    @Test
    void override_replaces_delegate_entry() {
        OverlayModelCatalog cat = new OverlayModelCatalog(InMemoryModelCatalog.defaults());
        cat.putOverride("pi.default", ModelDescriptor.builder()
                .useCase("pi.default").provider("dashscope").model("qwen-plus")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true).build());
        assertThat(cat.resolve("pi.default").getProvider()).isEqualTo("dashscope");
        assertThat(cat.resolve("pi.default").getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void override_does_not_change_other_registered_use_cases() {
        Map<String, ModelDescriptor> map = new LinkedHashMap<String, ModelDescriptor>();
        map.put("pi.default", InMemoryModelCatalog.defaultChatDescriptor());
        map.put("other", ModelDescriptor.builder()
                .useCase("other").provider("deepseek").model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true).build());
        OverlayModelCatalog cat = new OverlayModelCatalog(new InMemoryModelCatalog(map));
        cat.putOverride("pi.default", ModelDescriptor.builder()
                .useCase("pi.default").provider("dashscope").model("qwen-plus")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true).build());
        assertThat(cat.resolve("pi.default").getProvider()).isEqualTo("dashscope");
        assertThat(cat.resolve("other").getProvider()).isEqualTo("deepseek");
    }
}
