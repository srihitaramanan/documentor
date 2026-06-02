package com.srihitaramanan.documentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DocuMentor — production-grade RAG API for document question-answering.
 *
 * <p>Entry point. Virtual threads are enabled via the
 * {@code spring.threads.virtual.enabled} property in {@code application.yml}.
 */
@SpringBootApplication
public class DocumentorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentorApplication.class, args);
    }
}