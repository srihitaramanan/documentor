package com.srihitaramanan.documentor;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test of the auth flow against a real pgvector-enabled Postgres.
 *
 * <p>Boots the full Spring context, hits actual HTTP endpoints through
 * MockMvc, and verifies the entire path: validation → password hashing →
 * persistence → JWT issuance → token-based access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class AuthFlowIntegrationTest {

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

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register → login → access protected endpoint")
    void fullAuthFlow() throws Exception {
        String email = "srihita+%s@test.dev".formatted(System.nanoTime());
        String password = "supersecret123";

        // 1. Register — expect 201 + JWT
        String registerBody = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);

        String registerResponse = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode reg = json.readTree(registerResponse);
        String registrationToken = reg.get("token").asText();
        String userId = reg.get("userId").asText();
        assertThat(registrationToken).contains("."); // JWT has three dot-separated parts

        // 2. Login with the same credentials — expect 200 + a fresh JWT
        String loginBody = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);

        String loginResponse = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(userId))
                .andReturn().getResponse().getContentAsString();

        String loginToken = json.readTree(loginResponse).get("token").asText();
        assertThat(loginToken).isNotBlank();

        // 3. Hit the public smoke-test endpoint — should work without a token
        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("alive"));
    }

    @Test
    @DisplayName("register fails when email is invalid")
    void registerRejectsBadEmail() throws Exception {
        String body = """
                { "email": "not-an-email", "password": "supersecret123" }
                """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("register fails when password is too short")
    void registerRejectsShortPassword() throws Exception {
        String body = """
                { "email": "user@test.dev", "password": "short" }
                """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("register fails with 409 when email already exists")
    void registerRejectsDuplicateEmail() throws Exception {
        String email = "dup+%s@test.dev".formatted(System.nanoTime());
        String body = """
                { "email": "%s", "password": "supersecret123" }
                """.formatted(email);

        // First registration — succeeds
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second registration with same email — 409 Conflict
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    @DisplayName("login fails with 401 on wrong password")
    void loginRejectsWrongPassword() throws Exception {
        String email = "wrong+%s@test.dev".formatted(System.nanoTime());

        String registerBody = """
                { "email": "%s", "password": "supersecret123" }
                """.formatted(email);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                { "email": "%s", "password": "wrong-password" }
                """.formatted(email);
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    @DisplayName("login fails with 401 for unknown email")
    void loginRejectsUnknownEmail() throws Exception {
        String body = """
                { "email": "nobody+%s@test.dev", "password": "supersecret123" }
                """.formatted(System.nanoTime());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }
}