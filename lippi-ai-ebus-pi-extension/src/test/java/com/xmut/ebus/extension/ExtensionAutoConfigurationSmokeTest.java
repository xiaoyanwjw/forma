package com.xmut.ebus.extension;

import com.xmut.ebus.extension.config.EbusModelCatalogAutoConfiguration;
import com.xmut.ebus.extension.config.SkuToolsConfiguration;
import com.xmut.ebus.extension.config.XhsToolsConfiguration;
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
                        EbusModelCatalogAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(EbusModelCatalogAutoConfiguration.class);
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
        assertTrue(listed.contains("com.xmut.ebus.extension.config.SkuToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.ebus.extension.config.XhsToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.ebus.extension.config.ViewToolsConfiguration"));
        assertTrue(listed.contains("com.xmut.ebus.extension.config.EbusModelCatalogAutoConfiguration"));
    }
}
