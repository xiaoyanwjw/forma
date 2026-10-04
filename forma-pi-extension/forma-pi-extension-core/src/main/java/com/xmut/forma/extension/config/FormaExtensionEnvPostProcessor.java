package com.xmut.forma.extension.config;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Boot 2.7 only auto-loads the starter {@code application.yml}. This processor
 * also loads {@code application.yml} from {@code forma-pi-extension-*} jars so
 * scene-pack defaults ({@code forma.web-fetch.*} etc.) take effect.
 */
public class FormaExtensionEnvPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_PREFIX = "formaExtensionYml:";
    static final String CLASSPATH_PATTERN = "classpath*:application.yml";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment == null) {
            return;
        }
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(CLASSPATH_PATTERN);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + CLASSPATH_PATTERN, ex);
        }
        for (int i = 0; i < resources.length; i++) {
            addIfExtensionYml(environment, resources[i]);
        }
    }

    void addIfExtensionYml(ConfigurableEnvironment environment, Resource resource) {
        if (resource == null || !resource.exists() || !isFormaExtensionApplicationYml(resource)) {
            return;
        }
        String name = PROPERTY_SOURCE_PREFIX + resource.getDescription();
        if (environment.getPropertySources().contains(name)) {
            return;
        }
        List<PropertySource<?>> sources;
        try {
            sources = loader.load(name, resource);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resource, ex);
        }
        for (int i = 0; i < sources.size(); i++) {
            environment.getPropertySources().addLast(sources.get(i));
        }
    }

    static boolean isFormaExtensionApplicationYml(Resource resource) {
        try {
            URL url = resource.getURL();
            if (url == null) {
                return false;
            }
            String path = url.toString().replace('\\', '/');
            return path.contains("forma-pi-extension");
        } catch (IOException ex) {
            return false;
        }
    }
}
