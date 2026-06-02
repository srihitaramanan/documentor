package com.srihitaramanan.documentor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Trivial controller — confirms wiring works end-to-end.
 * Real domain controllers (auth, document, chat) come in later commits.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "meta", description = "Service metadata and wiring smoke tests")
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Smoke test", description = "Confirms the service is alive.")
    public Map<String, Object> hello() {
        return Map.of(
                "service", "documentor",
                "status", "alive",
                "timestamp", Instant.now().toString(),
                "message", "DocuMentor is running. See /swagger-ui.html for the full API."
        );
    }
}