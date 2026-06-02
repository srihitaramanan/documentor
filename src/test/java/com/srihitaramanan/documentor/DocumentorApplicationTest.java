package com.srihitaramanan.documentor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Day-1 smoke test: confirms the Spring context loads with a real
 * pgvector-enabled Postgres started via Testcontainers, Flyway runs
 * successfully, and Ollama auto-config is happy.
 */
@SpringBootTest
@Testcontainers
class DocumentorApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("documentor")
                    .withUsername("documentor")
                    .withPassword("documentor");

    /**
     * The test profile disables the Ollama health probe so the context loads
     * without a running Ollama instance.
     */
    @DynamicPropertySource
    static void disableOllama(DynamicPropertyRegistry r) {
        r.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
        r.add("spring.ai.ollama.init.pull-model-strategy", () -> "never");
    }

    @Test
    void contextLoads() {
        // If the context fails to load (bad config, missing bean, broken migration),
        // this test fails. That's the entire purpose on Day 1.
    }
}