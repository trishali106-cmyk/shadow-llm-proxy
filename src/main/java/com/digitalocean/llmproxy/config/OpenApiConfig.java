package com.digitalocean.llmproxy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the public proxy API.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "X-API-Key";

    @Bean
    OpenAPI shadowLlmProxyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shadow LLM Proxy")
                        .description("Synchronous primary LLM path with asynchronous candidate shadow comparison.")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
