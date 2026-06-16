package com.srihitaramanan.documentor.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code conversations} table.
 *
 * <p>A conversation belongs to one user and is optionally scoped to a
 * single document. If {@code documentId} is null, the conversation can
 * search across all of the user's documents.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Conversation() {}

    private Conversation(UUID id, UUID userId, UUID documentId, String title, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.documentId = documentId;
        this.title = title;
        this.createdAt = createdAt;
    }

    public static Conversation newConversation(UUID userId, UUID documentId, String title) {
        return new Conversation(
                UUID.randomUUID(), userId, documentId, title, OffsetDateTime.now()
        );
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getDocumentId() { return documentId; }
    public String getTitle() { return title; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation c)) return false;
        return id != null && id.equals(c.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}