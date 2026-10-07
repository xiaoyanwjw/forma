package com.xmut.forma.application.business.scene.pack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathSceneMetaCatalogTest {

    @Test
    void loadsEcommerceAndXiaohongshuFromTestClasspath() {
        ClasspathSceneMetaCatalog catalog = new ClasspathSceneMetaCatalog();

        SceneMeta ecommerce = catalog.find("ecommerce").orElse(null);
        assertThat(ecommerce).isNotNull();
        assertThat(ecommerce.getRequiredSkills()).containsExactly(
                "ecommerce-picklist", "ecommerce-skulist");
        assertThat(ecommerce.getDefaultSkill()).isEqualTo("ecommerce-picklist");

        SceneMeta xhs = catalog.find("xiaohongshu").orElse(null);
        assertThat(xhs).isNotNull();
        assertThat(xhs.getDefaultSkill()).isEqualTo("xhs-topiclist");
        assertThat(xhs.getRequiredSkills()).contains("xhs-note");

        SceneMeta product = catalog.find("tech_product").orElse(null);
        assertThat(product).isNotNull();
        assertThat(product.getDefaultSkill()).isEqualTo("tech-competitor");
        assertThat(product.getRequiredSkills()).containsExactly("tech-competitor");
    }
}
