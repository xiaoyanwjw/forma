package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.view.CatalogSkillTemplateLoader;
import com.xmut.forma.extension.tool.view.MustacheViewRenderer;
import com.xmut.forma.extension.tool.view.RenderViewToolHandler;
import com.xmut.forma.extension.tool.view.SkillTemplateLoader;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Registers {@code render_view}. Schema comes from {@code tools/view/render_view.tool.json}.
 */
@Configuration
public class ViewToolsConfiguration {

    @Bean
    public MustacheViewRenderer mustacheViewRenderer() {
        return new MustacheViewRenderer();
    }

    @Bean
    public SkillTemplateLoader skillTemplateLoader(SkillCatalog skillCatalog, ResourceLoader resourceLoader) {
        return new CatalogSkillTemplateLoader(skillCatalog, resourceLoader);
    }

    @Bean
    public RenderViewToolHandler renderViewToolHandler(SkillTemplateLoader skillTemplateLoader,
                                                       MustacheViewRenderer mustacheViewRenderer) {
        return new RenderViewToolHandler(skillTemplateLoader, mustacheViewRenderer);
    }
}
