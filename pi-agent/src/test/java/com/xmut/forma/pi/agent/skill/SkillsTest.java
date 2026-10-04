package com.xmut.forma.pi.agent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SkillsTest {

    @Test
    void loadsOfficialFrontmatterAndBodyRef() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-picklist\n"
                + "description: Picklist skill for Adam ecommerce.\n"
                + "allowed-tools: read_skill\n"
                + "---\n"
                + "\n"
                + "# Body\n"
                + "Rules here.\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-picklist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getId()).isEqualTo("ecommerce-picklist");
        assertThat(m.getDescription()).contains("Picklist");
        assertThat(m.getAllowedTools()).containsExactly("read_skill");
        assertThat(m.getSceneCode()).isEqualTo("ecommerce");
        assertThat(m.getPromptRef()).contains("scenes/ecommerce/ecommerce-picklist/SKILL.md");
    }

    @Test
    void parseFrontmatter_foldsBlockScalarDescription() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-picklist\n"
                + "description: >-\n"
                + "  经配置的商品检索用 search_sku 产出选品清单。\n"
                + "  在用户提到选品时使用。\n"
                + "  不要用于 Listing。\n"
                + "allowed-tools: read_skill search_sku\n"
                + "metadata:\n"
                + "  output:\n"
                + "    billing: true\n"
                + "    persistAs: picklist\n"
                + "---\n"
                + "\n"
                + "# Body\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-picklist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getDescription())
                .startsWith("经配置的商品检索")
                .contains("在用户提到选品时使用")
                .contains("不要用于 Listing")
                .doesNotContain(">-");
        assertThat(m.getAllowedTools()).containsExactly("read_skill", "search_sku");
        assertThat(m.getPersistAs()).isEqualTo("picklist");

        String catalog = SkillCatalogPrompt.build(
                java.util.Collections.singletonList(m), "ecommerce-picklist");
        assertThat(catalog).contains("- ecommerce-picklist: 经配置的商品检索");
        assertThat(catalog).contains("当前技能：ecommerce-picklist");
        assertThat(catalog).doesNotContain("- ecommerce-picklist: >-");
    }

    @Test
    void parseAllowedTools_splitsCommaSeparatedNames() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-skulist\n"
                + "description: Listing with HITL.\n"
                + "allowed-tools: ask_human, read_skill\n"
                + "metadata:\n"
                + "  output:\n"
                + "    persistAs: sku\n"
                + "    hideFromHistory: listing_plan\n"
                + "---\n"
                + "\n"
                + "# Body\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-skulist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getAllowedTools()).containsExactly("ask_human", "read_skill");
        assertThat(m.getPersistAs()).isEqualTo("sku");
        assertThat(m.getHideFromHistory()).containsExactly("listing_plan");
    }

    @Test
    void parse_readsOutputPaths() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-skulist\n"
                + "description: Listing skill.\n"
                + "allowed-tools: read_skill\n"
                + "metadata:\n"
                + "  output:\n"
                + "    viewPath: /views/listing\n"
                + "    artifactPath: artifacts/listing.md\n"
                + "    planViewPath: /views/listing-plan\n"
                + "    planArtifactPath: artifacts/listing-plan.md\n"
                + "---\n"
                + "\n"
                + "# Body\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-skulist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getViewPath()).isEqualTo("/views/listing");
        assertThat(m.getArtifactPath()).isEqualTo("artifacts/listing.md");
        assertThat(m.getPlanViewPath()).isEqualTo("/views/listing-plan");
        assertThat(m.getPlanArtifactPath()).isEqualTo("artifacts/listing-plan.md");
    }
}
