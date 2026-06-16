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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that the document upload endpoint accepts a file, persists a
 * document record, and rejects unauthenticated requests.
 *
 * <p>Note: full ingestion isn't tested end-to-end here because that would
 * require a running Ollama. The async ingestion path is exercised via
 * integration tests that mock the embedding model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentUploadIntegrationTest {

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
    @DisplayName("upload requires a valid JWT")
    void uploadRequiresAuth() throws Exception {
        var file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes());

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticated upload accepts file and returns 202")
    void authenticatedUploadAccepted() throws Exception {
        // Register a user and get a token
        String email = "doc+%s@test.dev".formatted(System.nanoTime());
        String body = """
                { "email": "%s", "password": "supersecret123" }
                """.formatted(email);

        String registerResp = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(registerResp).get("token").asText();

        // Upload a file
        var file = new MockMultipartFile(
                "file", "hello.txt", "text/plain",
                "Hello DocuMentor. This is a test document.".getBytes());

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("hello.txt"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}