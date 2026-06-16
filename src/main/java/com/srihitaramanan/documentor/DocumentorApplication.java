package com.srihitaramanan.documentor;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DocuMentor — production-grade RAG API for document question-answering.
 *
 * <p>Entry point. Virtual threads are enabled via the
 * {@code spring.threads.virtual.enabled} property in {@code application.yml}.
 */
@SpringBootApplication
@EnableAsync
public class DocumentorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentorApplication.class, args);
    }

    /**
     * Tells OpenAPI/Swagger that this API uses a bearer JWT.
     * Adds the "Authorize" button to Swagger UI so we can paste a token
     * once and have it attached to all subsequent requests.
     */
    @Bean
    public OpenAPI documentorOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}