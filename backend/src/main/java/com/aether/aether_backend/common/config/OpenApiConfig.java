package com.aether.aether_backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * OpenAPI metadata shown at the top of the generated spec and Swagger UI.
 * Endpoint-level documentation lives in the {@code @Tag}/{@code @Operation}
 * annotations on the controllers.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aetherOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Aether Platform API")
                .description("本地优先知识原子与主动连接发现平台。统一响应 `{code, message, data, timestamp}`；"
                        + "连接发现结果可通过 SSE 实时推送。")
                .version("v0.2"));
    }
}
