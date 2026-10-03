package com.xmut.forma;

import com.xmut.forma.extension.ExtensionScanProbeComponent;
import com.xmut.forma.extension.config.FormaModelCatalogAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FormaApplicationExtensionScanExcludeTest {

    @Test
    void formaApplication_keeps_boot_default_exclude_filters_and_extension_regex() {
        assertThat(Application.class.getAnnotation(SpringBootApplication.class)).isNull();
        assertThat(Application.class.getAnnotation(SpringBootConfiguration.class)).isNotNull();
        assertThat(Application.class.getAnnotation(EnableAutoConfiguration.class)).isNotNull();

        ComponentScan scan = Application.class.getAnnotation(ComponentScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.basePackages()).containsExactly("com.xmut.forma");

        ComponentScan.Filter[] filters = scan.excludeFilters();
        assertThat(filters).hasSize(3);
        assertThat(filters[0].type()).isEqualTo(FilterType.CUSTOM);
        assertThat(filters[0].classes()).containsExactly(TypeExcludeFilter.class);
        assertThat(filters[1].type()).isEqualTo(FilterType.CUSTOM);
        assertThat(filters[1].classes()).containsExactly(AutoConfigurationExcludeFilter.class);
        assertThat(filters[2].type()).isEqualTo(FilterType.REGEX);
        assertThat(filters[2].pattern()).containsExactly("com\\.xmut\\.forma\\.extension\\..*");
    }

    @Test
    void probe_is_visible_when_extension_package_is_scanned_without_forma_filters() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScanExtensionWithoutExclude.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(ExtensionScanProbeComponent.class));
    }

    @Test
    void does_not_component_scan_extension_types_but_loads_catalog_via_auto_config() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> {
                    ClassPathBeanDefinitionScanner scanner =
                            new ClassPathBeanDefinitionScanner((GenericApplicationContext) ctx, true);
                    applyFormaApplicationExcludeFilters(scanner);
                    scanner.scan("com.xmut.forma.extension");
                })
                .withConfiguration(AutoConfigurations.of(FormaModelCatalogAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ExtensionScanProbeComponent.class);
                    assertThat(ctx).hasSingleBean(FormaModelCatalogAutoConfiguration.class);
                });
    }

    private static void applyFormaApplicationExcludeFilters(ClassPathBeanDefinitionScanner scanner) {
        ComponentScan scan = Application.class.getAnnotation(ComponentScan.class);
        for (ComponentScan.Filter filter : scan.excludeFilters()) {
            if (filter.type() == FilterType.REGEX) {
                for (String pattern : filter.pattern()) {
                    scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(pattern)));
                }
            } else if (filter.type() == FilterType.CUSTOM) {
                for (Class<?> filterClass : filter.classes()) {
                    scanner.addExcludeFilter((TypeFilter) BeanUtils.instantiateClass(filterClass));
                }
            }
        }
    }

    @Configuration
    @ComponentScan(
            basePackages = "com.xmut.forma.extension",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = ExtensionScanProbeComponent.class))
    static class ScanExtensionWithoutExclude {
    }
}
