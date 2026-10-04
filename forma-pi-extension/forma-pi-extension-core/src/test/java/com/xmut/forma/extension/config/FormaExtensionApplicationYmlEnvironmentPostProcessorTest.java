package com.xmut.forma.extension.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormaExtensionApplicationYmlEnvironmentPostProcessorTest {

    @Test
    void loadsApplicationYmlFromExtensionClasspath() {
        StandardEnvironment environment = new StandardEnvironment();
        new FormaExtensionEnvPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());
        assertEquals("loaded-from-extension-yml", environment.getProperty("forma.test-extension-yml-marker"));
        boolean found = false;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().startsWith(
                    FormaExtensionEnvPostProcessor.PROPERTY_SOURCE_PREFIX)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
