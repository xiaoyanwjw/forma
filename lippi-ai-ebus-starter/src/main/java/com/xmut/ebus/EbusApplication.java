package com.xmut.ebus;

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
 * single scan with Boot's default exclude filters plus the extension regex.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.xmut.ebus",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.xmut\\.ebus\\.extension\\..*")
        })
public class EbusApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbusApplication.class, args);
    }
}
