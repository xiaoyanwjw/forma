package com.xmut.forma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Adam 唯一可启动入口。领域逻辑勿放本模块。
 * <p>
 * Boot 2.7 {@code @SpringBootApplication} has no {@code excludeFilters} attribute
 * (that alias exists only in Boot 3). Expanding the composed annotation keeps a
 * single scan with Boot's default exclude filters plus extension / Pi config regex.
 * Pi {@code AgentConfiguration} must not be component-scanned or it registers Stub
 * before {@code PiAiAutoConfiguration} can bind DeepSeek.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.xmut.forma",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.xmut\\.forma\\.(extension|pi\\.agent\\.config)\\..*")
        })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
