package com.srihitaramanan.documentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the chat endpoints enforce auth and basic happy-path creation.
 *
 * <p>The /ask endpoint isn't called here because it would require a
 * live Ollama instance; it's exercised manually via Swagger UI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChatEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("documentor")
                    .withUsername("documentor")
                    .withPassword("documentor");

    @DynamicPropertySource
    static void disableOllama(DynamicPropertyRegistry r) {
        r.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
        r.add("spring.ai.ollama.init.pull-model-strategy", () -> "never");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("creating a conversation requires auth")
    void createRequiresAuth() throws Exception {
        mvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticated user can create and list conversations")
    void createAndList() throws Exception {
        String email = "chat+%s@test.dev".formatted(System.nanoTime());
        String body = """
                { "email": "%s", "password": "supersecret123" }
                """.formatted(email);

        String registerResp = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(registerResp).get("token").asText();

        // Create a conversation
        mvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My questions\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("My questions"));

        // List should include it
        mvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("asking in non-existent or other-user conversation returns 404")
    void askInUnknownConvReturnsNotFound() throws Exception {
        String email = "chat+%s@test.dev".formatted(System.nanoTime());
        String body = """
                { "email": "%s", "password": "supersecret123" }
                """.formatted(email);

        String registerResp = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(registerResp).get("token").asText();

        // Random UUID — not their conversation
        String randomConvId = "00000000-0000-0000-0000-000000000000";
        mvc.perform(post("/api/conversations/" + randomConvId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is this?\"}"))
                .andExpect(status().isNotFound());
    }
}