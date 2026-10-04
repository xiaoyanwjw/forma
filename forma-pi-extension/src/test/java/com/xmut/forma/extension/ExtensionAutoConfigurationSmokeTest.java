package com.xmut.forma.extension;

import com.xmut.forma.extension.config.FormaModelCatalogAutoConfiguration;
import com.xmut.forma.extension.config.SkuToolsConfiguration;
import com.xmut.forma.extension.config.XhsToolsConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionAutoConfigurationSmokeTest {

    @Test
    void catalog_overlay_loads_from_spring_factories() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SkuToolsConfiguration.class,
                        XhsToolsConfiguration.class,
                        FormaModelCatalogAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FormaModelCatalogAutoConfiguration.class);
                    assertThat(ctx).hasSingleBean(SkuToolsConfiguration.class);
                    assertThat(ctx).hasSingleBean(XhsToolsConfiguration.class);
                });
    }

    @Test
    void spring_factories_lists_extension_configs() throws IOException {
        Enumeration<URL> urls = ClassLoader.getSystemResources("META-INF/spring.factories");
        StringBuilder merged = new StringBuilder();
        while (urls.hasMoreElements()) {
            Properties one = new Properties();
            try (InputStream in = urls.nextElement().openStream()) {
                one.load(in);
            }
            String value = one.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
            if (value != null) {
                merged.append(value).append(',');
            }
        }
        String listed = merged.toString();
        assertTrue(listed.contains("com.xmut.forma.extension.config.SkuToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.forma.extension.config.XhsToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.forma.extension.config.WebFetchToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.forma.extension.config.ViewToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.forma.extension.config.FormaModelCatalogAutoConfiguration"));
    }
}
