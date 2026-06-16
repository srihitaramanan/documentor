package com.srihitaramanan.documentor.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code messages} table.
 *
 * <p>For assistant messages, {@code citedChunkIds} records the chunk IDs
 * the LLM was given as context — the source-of-truth for citations.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cited_chunk_ids", columnDefinition = "uuid[]")
    private List<UUID> citedChunkIds;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Message() {}

    public Message(UUID id, UUID conversationId, MessageRole role, String content,
                   List<UUID> citedChunkIds, Integer promptTokens, Integer completionTokens,
                   String modelUsed, OffsetDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.citedChunkIds = citedChunkIds;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.modelUsed = modelUsed;
        this.createdAt = createdAt;
    }

    public static Message userMessage(UUID conversationId, String content) {
        return new Message(
                UUID.randomUUID(), conversationId, MessageRole.USER,
                content, null, null, null, null, OffsetDateTime.now()
        );
    }

    public static Message assistantMessage(UUID conversationId, String content,
                                           List<UUID> citedChunkIds,
                                           Integer promptTokens, Integer completionTokens,
                                           String modelUsed) {
        return new Message(
                UUID.randomUUID(), conversationId, MessageRole.ASSISTANT,
                content, citedChunkIds, promptTokens, completionTokens,
                modelUsed, OffsetDateTime.now()
        );
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public List<UUID> getCitedChunkIds() { return citedChunkIds; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public String getModelUsed() { return modelUsed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message m)) return false;
        return id != null && id.equals(m.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}