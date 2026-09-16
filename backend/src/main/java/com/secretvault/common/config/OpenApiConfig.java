package com.secretvault.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI secretVaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecretVault API")
                        .description("DevSecOps Secret Management & Security Control Plane REST API")
                        .version("0.1.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("SecretVault Engineering")
                                .url("https://github.com/Hrushi4151/SecretVault"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://github.com/Hrushi4151/SecretVault/blob/main/LICENSE")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
