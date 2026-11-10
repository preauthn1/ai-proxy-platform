package com.yunpei.proxy.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Knife4j-next 文档元数据配置。
 *
 * <p>说明:
 * <ul>
 *   <li>title / version / contact 通过 Java bean 注入,yml 不重复(避免 knife4j-next yml vs bean 谁覆盖谁的歧义)</li>
 *   <li>{@code bearerAuth} securityScheme 与 Sa-Token {@code Authorization: Bearer} 对齐</li>
 *   <li>{@code @RestControllerAdvice} 的方法要显式加 {@code @Operation} + {@code @ApiResponse},否则不会收录到 OpenAPI</li>
 * </ul>
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** 构造 OpenAPI 文档元数据（title / version / contact / securityScheme）。 */
    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("java-admin API 文档")
                        .description("Treasure 后台管理系统接口")
                        .version("1.0.0")
                        .contact(new Contact().name("yunpei")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")))
                .externalDocs(new ExternalDocumentation()
                        .description("Knife4j-next 文档页")
                        .url("/doc.html"));
    }
}
