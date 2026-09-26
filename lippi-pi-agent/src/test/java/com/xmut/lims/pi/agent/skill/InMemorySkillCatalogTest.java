package com.xmut.lims.pi.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InMemorySkillCatalogTest {

    private static Skill valid(String id) {
        return Skill.builder()
                .id(id)
                .description("desc for " + id)
                .promptRef("classpath:scenes/demo/" + id + "/SKILL.md")
                .allowedTools(Collections.emptyList())
                .build();
    }

    @Test
    void register_get_resolve_list_happy_path() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        Skill m = valid("demo.skill");
        registry.register(m);

        assertThat(registry.get("demo.skill")).contains(m);
        assertThat(registry.resolve("demo.skill")).isPresent();
        assertThat(registry.resolve("demo.skill").get().getDescription())
                .contains("desc for demo.skill");
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void listByScene_filters_on_scene_code() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(valid("a").toBuilder().sceneCode("ecommerce").build());
        registry.register(valid("b").toBuilder().sceneCode("other").build());
        registry.register(valid("c").toBuilder().sceneCode("ecommerce").build());

        assertThat(registry.listByScene("ecommerce"))
                .extracting(Skill::getId)
                .containsExactly("a", "c");
        assertThat(registry.listByScene("  ecommerce  "))
                .extracting(Skill::getId)
                .containsExactly("a", "c");
        assertThat(registry.listByScene("missing")).isEmpty();
        assertThat(registry.listByScene("")).isEmpty();
        assertThat(registry.listByScene(null)).isEmpty();
    }

    @Test
    void duplicate_id_refused() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(valid("a"));
        assertThatThrownBy(() -> registry.register(valid("a")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("duplicate");
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void replace_allowed_when_mutation_on() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(valid("a"));
        Skill replaced = valid("a").toBuilder()
                .description("replaced-desc")
                .build();
        registry.replace(replaced);
        assertThat(registry.resolve("a").get().getDescription()).isEqualTo("replaced-desc");
    }

    @Test
    void mutation_false_runtime_register_fails_bootstrap_ok() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        assertThat(registry.properties().isAllowRuntimeMutation()).isFalse();

        assertThatThrownBy(() -> registry.register(valid("demo.skill")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("runtime skill mutation disabled");

        registry.registerBootstrap(valid("demo.skill"));
        assertThat(registry.resolve("demo.skill")).isPresent();

        registry.sealBootstrap();
        assertThatThrownBy(() -> registry.registerBootstrap(valid("other")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("bootstrap window closed");

        assertThatThrownBy(() -> registry.unregister("demo.skill"))
                .isInstanceOf(SkillValidationException.class);
        assertThatThrownBy(() -> registry.replace(valid("demo.skill")))
                .isInstanceOf(SkillValidationException.class);
    }

    @Test
    void validation_rejects_null_allowed_tools() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        assertThatThrownBy(() -> registry.register(Skill.builder()
                .id("ok")
                .description("d")
                .promptRef("classpath:scenes/demo/ok/SKILL.md")
                .allowedTools(null)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("allowedTools");
    }

    @Test
    void validation_rejects_missing_and_illegal_fields() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());

        assertThatThrownBy(() -> registry.register(valid(null)))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("id");

        assertThatThrownBy(() -> registry.register(valid("bad id")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("whitespace");

        assertThatThrownBy(() -> registry.register(Skill.builder()
                .id("ok")
                .allowedTools(Collections.emptyList())
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("description");

        assertThatThrownBy(() -> registry.register(Skill.builder()
                .id("ok")
                .description("d")
                .allowedTools(Collections.emptyList())
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("promptRef");

        assertThatThrownBy(() -> registry.register(Skill.builder()
                .id("ok")
                .description("d")
                .promptRef("classpath:scenes/demo/ok/SKILL.md")
                .allowedTools(Arrays.asList("a", "  "))
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("allowedTools");

        assertThat(registry.all()).isEmpty();
    }

    @Test
    void no_skill_router_class_in_hermes() {
        assertThatThrownBy(() -> Class.forName("com.xmut.lims.pi.agent.skill.SkillRouter"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.xmut.lims.pi.agent.agent.SkillRouter"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
