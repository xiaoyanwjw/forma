package com.xmut.ebus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Adam 唯一可启动入口。领域逻辑勿放本模块。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.xmut.ebus",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.xmut\\.ebus\\.extension\\..*"))
public class EbusApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbusApplication.class, args);
    }
}
