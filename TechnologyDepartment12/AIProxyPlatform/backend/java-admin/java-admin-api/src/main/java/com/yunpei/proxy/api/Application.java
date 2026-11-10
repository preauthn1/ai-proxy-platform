package com.yunpei.proxy.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * java-admin 应用入口。
 *
 * <p>唯一带 {@code @SpringBootApplication} 的模块。
 *
 * @author yunpei
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {
            "com.yunpei.proxy.api",
            "com.yunpei.proxy.infra",
            "com.yunpei.proxy.service",
            "com.yunpei.proxy.common"
        })
public class Application {

    private Application() {}

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
