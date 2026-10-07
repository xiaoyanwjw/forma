package com.xmut.forma.extension.config;

import com.xmut.forma.common.output.RunAttachProvider;
import com.xmut.forma.extension.output.CatalogRunAttachmentProvider;
import com.xmut.forma.extension.output.TurnReminderExtension;
import com.xmut.forma.extension.output.WorkspaceOutputParser;
import com.xmut.forma.extension.tool.view.CatalogSkillTemplateLoader;
import com.xmut.forma.extension.tool.view.MustacheViewRenderer;
import com.xmut.forma.extension.tool.view.RenderViewToolHandler;
import com.xmut.forma.extension.tool.view.SkillTemplateLoader;
import com.xmut.forma.extension.tool.view.ViewEnricher;
import com.xmut.forma.extension.tool.view.ViewEnricherComposite;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

/**
 * Registers {@code render_view} and the core turn-slot reminder.
 * Schema comes from {@code tools/view/render_view.tool.json}.
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
    public ViewEnricherComposite viewEnricherComposite(List<ViewEnricher> enrichers) {
        return new ViewEnricherComposite(enrichers);
    }

    @Bean
    public RenderViewToolHandler renderViewToolHandler(SkillTemplateLoader skillTemplateLoader,
                                                       MustacheViewRenderer mustacheViewRenderer,
                                                       ViewEnricherComposite viewEnricherComposite) {
        return new RenderViewToolHandler(skillTemplateLoader, mustacheViewRenderer, viewEnricherComposite);
    }

    @Bean
    @Primary
    public RunAttachProvider runAttachProvider(SkillCatalog skillCatalog) {
        return new CatalogRunAttachmentProvider(skillCatalog);
    }

    @Bean
    public TurnReminderExtension turnReminderExtension() {
        return new TurnReminderExtension();
    }

    @Bean
    public WorkspaceOutputParser workspaceOutputParser() {
        return new WorkspaceOutputParser();
    }
}
