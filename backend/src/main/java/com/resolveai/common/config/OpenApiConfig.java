package com.resolveai.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger Documentation Configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resolveAiOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ResolveAI API")
                .description("AI-Powered Enterprise Incident & Service Management Platform REST API")
                .version("v0.1.0")
                .contact(new Contact()
                    .name("ResolveAI Engineering Team")
                    .email("support@resolveai.internal"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://resolveai.internal/license")));
    }
}
