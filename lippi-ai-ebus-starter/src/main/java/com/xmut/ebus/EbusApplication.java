package com.xmut.ebus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Adam 唯一可启动入口。领域逻辑勿放本模块。
 */
@SpringBootApplication(scanBasePackages = "com.xmut.ebus")
public class EbusApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbusApplication.class, args);
    }
}
